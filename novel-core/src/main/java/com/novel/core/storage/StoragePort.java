package com.novel.core.storage;

import java.time.Duration;

/**
 * 对象存储端口。
 *
 * <p>core 只关心「给我一个能上传的地址」和「这个 key 的公开地址是什么」，
 * 不关心背后是 MinIO、S3 还是阿里云 OSS。换实现只改 infra，core 一行不动。
 *
 * <p>为什么用「预签名直传」而不是「后端中转」：
 * 后端中转意味着图片字节流要穿过应用服务器，占线程、占内存、占带宽；
 * 预签名把上传动作直接交给浏览器和对象存储，后端只签发一个 10 分钟有效的 URL。
 */
public interface StoragePort {

    /**
     * 生成一个只允许 PUT 指定对象的预签名地址。
     *
     * @param objectKey   对象键，形如 {@code cover/2026/11/02/xxx.jpg}
     * @param contentType 必须与上传时的 Content-Type 一致，否则签名校验失败
     * @param ttl         有效期
     * @return 预签名 URL
     */
    String presignPut(String objectKey, String contentType, Duration ttl);

    /** 对象的公开访问地址（bucket 需为公共读）。 */
    String publicUrl(String objectKey);

    /** 幂等地确保 bucket 存在且允许匿名读，供应用启动时调用。 */
    void ensureBucket();
}
