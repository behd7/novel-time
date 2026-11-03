package com.novel.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 应用入口。
 *
 * <p>扫描根包是 {@code com.novel}，因为 infra 目录下的配置类、Mapper 都要被扫到。
 * 如果只扫 {@code com.novel.api}，会出现「Bean 找不到」的经典事故。
 *
 * <p>排除 {@link UserDetailsServiceAutoConfiguration}：它会在启动日志里打印一个
 * 自动生成的内存用户密码（{@code Using generated security password: xxxx}）。
 * 我们走的是 JWT 自建认证，这个用户既用不上，又容易让人误以为系统有默认可登录账号。
 */
@SpringBootApplication(scanBasePackages = "com.novel",
        exclude = UserDetailsServiceAutoConfiguration.class)
public class NovelApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelApplication.class, args);
    }
}
