package com.novel.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.api.web.TraceIdFilter;
import com.novel.common.result.Result;
import com.novel.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * 安全链路的 JSON 响应工具。
 *
 * <p>为什么要单独抽出来：Spring Security 的 401/403 发生在进入 Controller **之前**，
 * 那时 {@code @RestControllerAdvice} 还没机会介入，必须自己写响应体。
 * 如果这里不写，前端会收到一个空的 401，跟统一响应格式对不上，
 * 前端拦截器就没法统一处理 —— 这是接入 Security 时最常见的不一致。
 */
final class SecurityResponses {

    private static final String CONTENT_TYPE = "application/json;charset=UTF-8";

    private SecurityResponses() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper,
                      int httpStatus, ResultCode resultCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(httpStatus);
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> body = Result.fail(resultCode, message == null ? resultCode.getMessage() : message);
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        objectMapper.writeValue(response.getWriter(), traceId == null ? body : body.withTraceId(traceId));
    }
}
