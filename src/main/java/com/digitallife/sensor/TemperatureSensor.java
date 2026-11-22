package com.digitallife.sensor;

import io.agentscope.core.message.TextBlock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * 电脑体感温度感官（模拟数字生命的皮肤冷热触觉）。
 */
@Component
public class TemperatureSensor implements Sensor {

    public static final String NAME = "temperature";

    private final SystemInfo oshiSystemInfo;
    private final NvidiaTemperature gpuTemperature = new NvidiaTemperature();
    private volatile boolean enabled;
    private long[] prevTicks;

    public TemperatureSensor(
            @Value("${digitallife.sensor.temperature.enabled:true}") boolean enabled
    ) {
        this.oshiSystemInfo = new SystemInfo();
        this.enabled = enabled;
        this.prevTicks = oshiSystemInfo.getHardware().getProcessor().getSystemCpuLoadTicks();
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
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public SensorySignal sense() {
        HardwareAbstractionLayer hal = oshiSystemInfo.getHardware();
        double degrees = hal.getSensors().getCpuTemperature();

        CentralProcessor processor = hal.getProcessor();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks);
        this.prevTicks = processor.getSystemCpuLoadTicks();

        String source = "CPU";
        String sourceLabel = "CPU 实测";
        if (degrees <= 0.0 || !Double.isFinite(degrees)) {
            OptionalDouble gpu = gpuTemperature.readCelsius();
            if (gpu.isPresent()) {
                degrees = gpu.getAsDouble();
                source = "NVIDIA_GPU";
                sourceLabel = "GPU 实测";
            } else {
                degrees = Math.round((42.0 + Math.max(0.0, cpuLoad) * 40.0) * 10.0) / 10.0;
                source = "CPU_LOAD_ESTIMATE";
                sourceLabel = "按 CPU 负载估算";
            }
        }

        boolean estimated = "CPU_LOAD_ESTIMATE".equals(source);
        boolean hot = degrees >= 78.0;
        String feeling;
        if (hot) {
            feeling = String.format("感觉有些热（%.1f°C）", degrees);
        } else if (degrees >= 62.0) {
            feeling = String.format("感觉暖暖的（%.1f°C）", degrees);
        } else {
            feeling = String.format("体感凉快舒适（%.1f°C）", degrees);
        }

        String summary = "[体感温度・" + sourceLabel + "] " + feeling;
        return new SensorySignal(
                NAME,
                Instant.now(),
                List.of(TextBlock.builder().text(summary).build()),
                summary,
                Map.of(
                        "temperatureCelsius", degrees,
                        "temperatureSource", source,
                        "cpuLoad", Math.round(cpuLoad * 1000.0) / 10.0,
                        "estimated", estimated,
                        "noteworthy", hot && !estimated
                )
        );
    }
}
