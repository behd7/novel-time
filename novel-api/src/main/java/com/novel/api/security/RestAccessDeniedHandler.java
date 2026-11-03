package com.novel.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 已认证但无权限：例如普通用户访问 {@code /admin/**}。
 */
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityResponses.write(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                ResultCode.FORBIDDEN, null);
    }
}
