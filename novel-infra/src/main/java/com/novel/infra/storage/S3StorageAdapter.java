package com.novel.infra.storage;

import com.novel.core.storage.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * 基于 S3 协议的存储实现。
 *
 * <p>刻意用 AWS SDK 而不是 MinIO 专用 SDK：MinIO 只是 S3 协议的一个实现，
 * 将来换 OSS / Ceph / AWS S3 时，这里只改 endpoint 和密钥，不需要重写代码。
 */
@Slf4j
@RequiredArgsConstructor
public class S3StorageAdapter implements StoragePort {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties props;

    @Override
    public String presignPut(String objectKey, String contentType, Duration ttl) {
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(b -> b
                        .bucket(props.getBucket())
                        .key(objectKey)
                        .contentType(contentType))
                .build();
        return presigner.presignPutObject(request).url().toString();
    }

    @Override
    public String publicUrl(String objectKey) {
        String base = props.getPublicEndpoint();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + props.getBucket() + "/" + objectKey;
    }

    @Override
    public void ensureBucket() {
        String bucket = props.getBucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return;
        } catch (NoSuchBucketException e) {
            log.info("bucket [{}] 不存在，自动创建", bucket);
        } catch (SdkException e) {
            // headBucket 在某些实现下会返回 404 而非 NoSuchBucketException
            log.info("bucket [{}] 探测失败({})，尝试创建", bucket, e.getMessage());
        }

        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored) {
            // 并发创建，忽略
        }

        // 封面/头像这类资源需要被浏览器直接 <img src> 引用，所以给只读策略
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucket);
        try {
            s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(bucket).policy(policy).build());
            log.info("bucket [{}] 已就绪（公共读）", bucket);
        } catch (SdkException e) {
            log.warn("bucket [{}] 设置公共读策略失败，图片将无法匿名访问：{}", bucket, e.getMessage());
        }
    }
}
