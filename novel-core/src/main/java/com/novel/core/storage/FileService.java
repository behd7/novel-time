package com.novel.core.storage;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传应用服务。
 *
 * <p>职责边界：决定 objectKey 长什么样、哪些类型允许上传、URL 有效期多久。
 * 这些都是业务规则，必须留在 core 里，而不是写死在 Controller 或 Storage 实现里。
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    private static final int MAX_EXT_LENGTH = 8;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "text/plain", "application/epub+zip", "application/zip"
    );

    private final StoragePort storagePort;

    public PresignedUpload presignUpload(String bizType, String fileName, String contentType) {
        FileBizType type;
        try {
            type = FileBizType.from(bizType);
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, e.getMessage());
        }

        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的文件类型: " + contentType);
        }

        String objectKey = buildObjectKey(type, fileName);
        String uploadUrl = storagePort.presignPut(objectKey, normalizedType, UPLOAD_TTL);
        return new PresignedUpload(objectKey, uploadUrl, storagePort.publicUrl(objectKey), UPLOAD_TTL.toSeconds());
    }

    /**
     * key 形如 {@code cover/2026/11/02/8f3c1d2e.jpg}。
     *
     * <p>按日期分目录是为了将来对象多了以后，运维清理、生命周期策略好按前缀批量处理；
     * 文件名用 UUID 而不是原始文件名，避免同名覆盖和中文/特殊字符在不同系统上的编码问题。
     */
    private String buildObjectKey(FileBizType type, String fileName) {
        String ext = extractExtension(fileName);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        java.time.LocalDate today = java.time.LocalDate.now();
        return String.format("%s/%04d/%02d/%02d/%s%s",
                type.path(),
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                uuid, ext);
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.length() > MAX_EXT_LENGTH || !ext.matches("[a-z0-9]+")) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件扩展名不合法");
        }
        return "." + ext;
    }
}
