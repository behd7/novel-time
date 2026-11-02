package com.novel.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 *
 * <p>扫描根包是 {@code com.novel}，因为 infra 目录下的配置类、Mapper 都要被扫到。
 * 如果只扫 {@code com.novel.api}，会出现「Bean 找不到」的经典事故。
 */
@SpringBootApplication(scanBasePackages = "com.novel")
public class NovelApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelApplication.class, args);
    }
}
