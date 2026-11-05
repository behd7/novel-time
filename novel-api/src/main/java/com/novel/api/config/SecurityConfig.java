package com.novel.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.api.security.JwtAuthenticationFilter;
import com.novel.api.security.RestAccessDeniedHandler;
import com.novel.api.security.RestAuthenticationEntryPoint;
import com.novel.core.user.port.TokenPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（无状态 JWT 模式）。
 *
 * <p>几个刻意的选择：
 * <ul>
 *   <li><b>关 CSRF</b>：没有 Cookie 会话，令牌靠请求头携带，浏览器不会自动带上，
 *       天然不受 CSRF 影响。反过来，开着 CSRF 会让所有 POST 都要 token，纯属自找麻烦。</li>
 *   <li><b>STATELESS</b>：不创建 HttpSession，横向扩容时不用做会话共享。</li>
 *   <li><b>去掉 formLogin / httpBasic</b>：默认的表单登录会返回一个 HTML 登录页，
 *       对纯 API 服务是噪音，而且会把 401 变成 302 重定向。</li>
 * </ul>
 *
 * <p>放行规则里有个必须注意的顺序陷阱：{@code GET /api/v1/users/*} 的通配符**同样会匹配
 * {@code /users/me}**。所以"查自己"必须先用更精确的规则声明为需要认证，
 * 否则匿名请求也能拿到"我的资料"接口 —— 它取不到用户，会返回 401，
 * 但错误来源就变得莫名其妙了。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    /** 完全公开的路径。 */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/api/v1/system/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenPort tokenPort,
                                                   ObjectMapper objectMapper) throws Exception {
        // 手动 new，避免被 Spring Boot 当成普通 Servlet Filter 重复注册
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenPort);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 阅读入口免登录：列表、详情、目录、正文、分类
                        .requestMatchers(HttpMethod.GET, "/api/v1/novels/**", "/api/v1/categories").permitAll()
                        // 搜索同理：它是找书的第一入口，要求登录等于把新用户挡在门外
                        .requestMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()
                        // 榜单也是公开入口：它读的是快照，和阅读接口同级别的曝光面
                        .requestMatchers(HttpMethod.GET, "/api/v1/ranks").permitAll()
                        // 社区浏览免登录：帖子列表、详情、评论列表都算公开内容
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                        // 书评的楼中楼是独立路径，不能靠 /novels/** 覆盖
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/*/replies").permitAll()
                        // 顺序要点：更精确的 /users/me 必须排在 /users/* 前面
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
