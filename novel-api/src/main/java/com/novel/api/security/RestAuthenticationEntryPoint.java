package com.novel.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 未认证入口：没有令牌、令牌过期、令牌伪造都走这里。
 */
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 过滤器里如果解析失败，会把更精确的原因放在这里（例如"登录已过期"）
        Object reason = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR);
        SecurityResponses.write(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                ResultCode.UNAUTHORIZED, reason == null ? null : reason.toString());
    }
}
