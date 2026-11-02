package com.novel.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置 —— 本项目没有前端，Swagger UI 就是演示界面，所以值得认真配。
 *
 * <p>提前把 bearerAuth 安全方案挂上，M1 接入 JWT 后，
 * 在 Swagger 右上角 "Authorize" 里填一次 token，全站接口都能带着它调试。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI novelOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Novel 小说平台 API")
                        .version("v1")
                        .description("""
                                小说阅读 + 社区平台后端。

                                **响应约定**：统一 `{code, message, data, traceId}`，`code=0` 为成功。
                                业务失败返回 HTTP 200 + 非 0 业务码；参数/协议错误返回 4xx；系统异常返回 500。
                                """)
                        .contact(new Contact().name("novel"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后获得的 accessToken，直接粘进来即可，不需要加 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
