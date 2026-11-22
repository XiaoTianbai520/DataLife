package com.digitallife;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableAsync(proxyTargetClass = true)
public class DigitalLifeApplication {

    public static void main(String[] args) throws IOException {
        // 确保 SQLite 数据目录存在
        Files.createDirectories(Path.of("data"));

        // 关闭 AWT Headless 模式，以便 VisionSensor 使用 java.awt.Robot 进行桌面截图
        new SpringApplicationBuilder(DigitalLifeApplication.class)
                .headless(false)
                .run(args);
    }
}
