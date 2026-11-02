package com.novel.core.storage;

/**
 * 预签名上传结果。
 *
 * @param objectKey 对象键，客户端上传成功后需把它回传给业务接口（如设置头像、封面）
 * @param uploadUrl 预签名 PUT 地址，客户端直接 PUT 二进制内容
 * @param publicUrl 公开访问地址，前端可直接渲染
 * @param expiresIn 有效期（秒）
 */
public record PresignedUpload(String objectKey, String uploadUrl, String publicUrl, long expiresIn) {
}
