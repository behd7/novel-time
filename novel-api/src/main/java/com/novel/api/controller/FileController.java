package com.novel.api.controller;

import com.novel.api.controller.dto.PresignRequest;
import com.novel.common.result.Result;
import com.novel.core.storage.FileService;
import com.novel.core.storage.PresignedUpload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件上传入口。
 *
 * <p>后端不碰文件流，只签发地址。典型流程：
 * <pre>
 *   1. POST /api/v1/files/presign  {bizType, fileName, contentType}  -> {objectKey, uploadUrl}
 *   2. 前端 PUT uploadUrl，body 为文件内容，Header 必须带同一个 Content-Type
 *   3. 把 objectKey 回传给业务接口（例如修改头像时传 avatarKey）
 * </pre>
 */
@Tag(name = "文件", description = "对象存储预签名上传")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "获取预签名上传地址", description = "有效期 10 分钟，只能 PUT 一次指定对象")
    @PostMapping("/presign")
    public Result<PresignedUpload> presign(@Valid @RequestBody PresignRequest request) {
        return Result.ok(fileService.presignUpload(
                request.bizType(), request.fileName(), request.contentType()));
    }
}
