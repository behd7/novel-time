package com.novel.api.security;

import com.novel.common.exception.BizException;
import com.novel.core.user.port.TokenPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>只做一件事：把 {@code Authorization: Bearer xxx} 解析成 {@code SecurityContext} 里的登录态。
 * 解析失败**不直接返回 401**，而是把原因塞进 request attribute 交给
 * {@code RestAuthenticationEntryPoint}。原因是：有些路径本来就允许匿名访问
 * （Swagger、健康检查），过滤器抛异常会把它们一起拦掉。
 *
 * <p>注意：这个类**没有** {@code @Component}。它由 {@code SecurityConfig} 手动 new 出来塞进
 * 过滤器链。如果标了 {@code @Component}，Spring Boot 会把它当成普通 Servlet Filter
 * 再注册一次，导致每个请求执行两遍 —— 一个很经典的坑。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 解析失败的原因，供 EntryPoint 取用。 */
    public static final String ATTR_ERROR = "novel.jwt.error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenPort tokenPort;

    public JwtAuthenticationFilter(TokenPort tokenPort) {
        this.tokenPort = tokenPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                TokenPort.Claims claims = tokenPort.parse(token, TokenPort.Type.ACCESS);
                LoginUser loginUser = new LoginUser(claims.userId(), claims.username());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BizException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(ATTR_ERROR, e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
