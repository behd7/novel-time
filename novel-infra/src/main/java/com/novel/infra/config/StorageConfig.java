package com.novel.infra.config;

import com.novel.core.storage.StoragePort;
import com.novel.infra.storage.S3StorageAdapter;
import com.novel.infra.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 对象存储装配。
 *
 * <p>两个容易踩的坑：
 * <ol>
 *   <li>{@code pathStyleAccessEnabled(true)} —— MinIO 用路径风格 {@code /bucket/key}，
 *       不开会按虚拟主机风格生成 {@code bucket.127.0.0.1} 这种域名，DNS 直接解析失败。</li>
 *   <li>S3Client 和 S3Presigner 必须使用完全相同的 endpoint/region/凭证，
 *       否则签出来的 URL 和目标服务对不上，报 SignatureDoesNotMatch。</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    private static final S3Configuration PATH_STYLE = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build();

    private StaticCredentialsProvider credentials(StorageProperties props) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(PATH_STYLE)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(PATH_STYLE)
                .build();
    }

    @Bean
    public StoragePort storagePort(S3Client s3Client, S3Presigner s3Presigner, StorageProperties props) {
        return new S3StorageAdapter(s3Client, s3Presigner, props);
    }
}
