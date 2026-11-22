package com.digitallife.sensor;

import com.digitallife.repository.BotConfigRepository;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 桌面视觉感官插件：
 * 使用 java.awt.Robot 截屏 -> Graphics2D 双线性插值等比缩放至 720p -> ImageIO 内存 JPEG 压缩。
 * 严格遵循“阅后即焚”，仅在内存中生成 ImageBlock，绝不写入磁盘或数据库。
 */
@Component
public class VisionSensor implements Sensor {

    private static final Logger log = LoggerFactory.getLogger(VisionSensor.class);
    public static final String NAME = "vision";

    private final BotConfigRepository configRepository;
    private volatile boolean enabled;
    private final int defaultTargetWidth;
    private final float jpegQuality;
    private Robot robot;

    public VisionSensor(
            BotConfigRepository configRepository,
            @Value("${digitallife.sensor.vision.enabled:true}") boolean enabled,
            @Value("${digitallife.sensor.vision.target-width:1280}") int defaultTargetWidth,
            @Value("${digitallife.sensor.vision.jpeg-quality:0.75}") float jpegQuality
    ) {
        this.configRepository = configRepository;
        this.enabled = enabled;
        this.defaultTargetWidth = defaultTargetWidth;
        this.jpegQuality = jpegQuality;
    }

    @PostConstruct
    synchronized void warmCapture() {
        if (!enabled || GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            robot = new Robot();
            Toolkit.getDefaultToolkit().getScreenSize();
            encodeJpeg(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB));
        } catch (AWTException | IOException exception) {
            log.info("桌面视觉预热未完成 ({})", exception.getClass().getSimpleName());
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public synchronized SensorySignal sense() {
        if (!enabled) {
            return null;
        }
        long startNs = System.nanoTime();
        int targetWidth = Math.max(1, configRepository.getInt(BotConfigRepository.KEY_VISION_TARGET_WIDTH, defaultTargetWidth));

        if (GraphicsEnvironment.isHeadless()) {
            String summary = "[桌面视觉] 当前运行在 Headless 环境，未采集屏幕画面";
            return new SensorySignal(
                    NAME,
                    Instant.now(),
                    List.of(TextBlock.builder().text(summary).build()),
                    summary,
                    Map.of("headless", true)
            );
        }

        try {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int srcWidth = Math.max(1, screenSize.width);
            int srcHeight = Math.max(1, screenSize.height);

            int dstWidth = Math.min(targetWidth, srcWidth);
            int dstHeight = Math.max(1, (int) Math.round((double) srcHeight * dstWidth / srcWidth));

            byte[] jpegBytes = captureAndDownsample(srcWidth, srcHeight, dstWidth, dstHeight);
            long costMs = (System.nanoTime() - startNs) / 1_000_000L;

            String summary = String.format("[桌面视觉] 已观察当前桌面画面 (%dx%d JPEG, %d KB, 耗时 %dms)",
                    dstWidth, dstHeight, jpegBytes.length / 1024, costMs);

            List<ContentBlock> blocks = new ArrayList<>(2);
            blocks.add(TextBlock.builder().text(summary).build());
            blocks.add(new ImageBlock(new Base64Source("image/jpeg", Base64.getEncoder().encodeToString(jpegBytes))));

            return new SensorySignal(
                    NAME,
                    Instant.now(),
                    blocks,
                    summary,
                    Map.of(
                            "width", dstWidth,
                            "height", dstHeight,
                            "byteSize", jpegBytes.length,
                            "costMs", costMs
                    )
            );
        } catch ( Exception e) {
            log.warn("桌面视觉采集失败，降级为纯文本提示: {}", e.getMessage());
            String summary = "[桌面视觉] 屏幕画面暂时无法看清";
            return new SensorySignal(
                    NAME,
                    Instant.now(),
                    List.of(TextBlock.builder().text(summary).build()),
                    summary,
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "capture_error")
            );
        }
    }

    private byte[] captureAndDownsample(int srcWidth, int srcHeight, int dstWidth, int dstHeight) throws Exception {
        if (robot == null) {
            robot = new Robot();
        }
        BufferedImage rawCapture = robot.createScreenCapture(new Rectangle(0, 0, srcWidth, srcHeight));

        BufferedImage scaled = new BufferedImage(dstWidth, dstHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(rawCapture, 0, 0, dstWidth, dstHeight, null);
        } finally {
            g2d.dispose();
            rawCapture.flush();
        }

        return encodeJpeg(scaled);
    }

    private byte[] encodeJpeg(BufferedImage scaled) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            scaled.flush();
            throw new IOException("当前 Java 运行时没有 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, jpegQuality)));
            }
            writer.write(null, new IIOImage(scaled, null, null), param);
        } finally {
            writer.dispose();
            scaled.flush();
        }
        return baos.toByteArray();
    }
}
