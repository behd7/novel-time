package com.novel.infra.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置。
 *
 * <p>{@code endpoint} 是后端访问对象存储的地址；{@code publicEndpoint} 是写进 URL 给浏览器用的地址。
 * 两者分开是因为生产环境往往不同：后端走内网 {@code http://minio:9000}，
 * 浏览器走公网 {@code https://cdn.example.com}。现在本地开发两者都是 127.0.0.1，但配置项先留出来。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "novel.storage")
public class StorageProperties {

    private String endpoint = "http://127.0.0.1:9000";
    private String publicEndpoint = "http://127.0.0.1:9000";
    private String accessKey = "novel";
    private String secretKey = "novel12345";
    private String bucket = "novel";
    /** MinIO 不校验 region，但 AWS SDK 要求必填，随便给一个即可。 */
    private String region = "us-east-1";
}
