package com.novel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证链路端到端测试。
 *
 * <p>用 Testcontainers 起真实的 MySQL 8 与 Redis，跑完整的 Spring 上下文：
 * Flyway 迁移 → MyBatis 落库 → BCrypt 校验 → JWT 签发 → Security 过滤器链 → Controller。
 * 单元测试证明的是"逻辑对"，这个测试证明的是"接起来也对" —— 两者缺一不可。
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("novel")
            .withUsername("novel")
            .withPassword("novel");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("novel.jwt.secret", () -> "integration-test-secret-0123456789abcdef");
        // 测试里同一个 127.0.0.1 要打很多次登录，把限流阈值调高，避免测试互相干扰
        registry.add("novel.auth.rate-limit.login-max-attempts", () -> "1000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 注册

    @Test
    @DisplayName("注册成功：201 + 双令牌 + 资料落库")
    void register_shouldReturnTokens() throws Exception {
        JsonNode body = register("alice01");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(body.path("data").path("refreshToken").asText()).isNotBlank();
        assertThat(body.path("data").path("expiresIn").asLong()).isBetween(1L, 1801L);
        assertThat(body.path("data").path("user").path("username").asText()).isEqualTo("alice01");
        assertThat(body.path("data").path("user").path("nickname").asText()).isEqualTo("昵称alice01");
    }

    @Test
    @DisplayName("注册不传昵称：默认用用户名兜底")
    void register_withoutNickname_shouldFallbackToUsername() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice10","password":"pwd123456"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("data").path("user").path("nickname").asText()).isEqualTo("alice10");
    }

    @Test
    @DisplayName("重复注册：返回业务码 10101，HTTP 仍是 200")
    void register_duplicateUsername_shouldReturnBusinessCode() throws Exception {
        register("alice02");

        JsonNode body = postJson("/api/v1/auth/register", registerPayload("alice02"));

        assertThat(body.path("code").asInt()).isEqualTo(10101);
    }

    @Test
    @DisplayName("用户名不合法：400 + 字段错误信息")
    void register_invalidUsername_shouldReturn400() throws Exception {
        String payload = """
                {"username":"ab","password":"pwd123456"}
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- 登录

    @Test
    @DisplayName("登录成功后可访问 /users/me")
    void login_thenAccessProtectedEndpoint() throws Exception {
        register("alice03");

        JsonNode login = postJson("/api/v1/auth/login", """
                {"username":"alice03","password":"pwd123456"}
                """);
        assertThat(login.path("code").asInt()).isZero();
        String accessToken = login.path("data").path("accessToken").asText();

        MvcResult me = mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode meBody = readJson(me);
        assertThat(meBody.path("code").asInt()).isZero();
        assertThat(meBody.path("data").path("username").asText()).isEqualTo("alice03");
    }

    @Test
    @DisplayName("密码错误与用户不存在返回同一个业务码，防止用户名枚举")
    void login_failureCodesMustBeIdentical() throws Exception {
        register("alice04");

        JsonNode wrongPassword = postJson("/api/v1/auth/login", """
                {"username":"alice04","password":"wrong-password"}
                """);
        JsonNode unknownUser = postJson("/api/v1/auth/login", """
                {"username":"no-such-user","password":"wrong-password"}
                """);

        assertThat(wrongPassword.path("code").asInt()).isEqualTo(10103);
        assertThat(unknownUser.path("code").asInt()).isEqualTo(10103);
        assertThat(wrongPassword.path("message").asText())
                .isEqualTo(unknownUser.path("message").asText());
    }

    @Test
    @DisplayName("不带令牌访问受保护接口：401 + 统一响应体")
    void me_withoutToken_shouldReturn401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("code").asInt()).isEqualTo(40100);
        assertThat(body.path("traceId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("伪造令牌：401")
    void me_withForgedToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 刷新

    @Test
    @DisplayName("刷新成功并轮换：旧 refreshToken 立即失效")
    void refresh_shouldRotateToken() throws Exception {
        JsonNode registered = register("alice05");
        String oldRefresh = registered.path("data").path("refreshToken").asText();

        JsonNode refreshed = postJson("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(oldRefresh));

        assertThat(refreshed.path("code").asInt()).isZero();
        assertThat(refreshed.path("data").path("refreshToken").asText()).isNotEqualTo(oldRefresh);

        JsonNode reused = postJson("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(oldRefresh));
        assertThat(reused.path("code").asInt()).isEqualTo(10105);
    }

    @Test
    @DisplayName("access 令牌不能拿来刷新，refresh 令牌也不能拿来访问")
    void tokenTypesMustNotBeInterchangeable() throws Exception {
        JsonNode registered = register("alice06");
        String access = registered.path("data").path("accessToken").asText();
        String refresh = registered.path("data").path("refreshToken").asText();

        JsonNode usedAsRefresh = postJson("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(access));
        assertThat(usedAsRefresh.path("code").asInt()).isEqualTo(10105);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登出后 refreshToken 失效")
    void logout_shouldRevokeRefreshToken() throws Exception {
        JsonNode registered = register("alice07");
        String access = registered.path("data").path("accessToken").asText();
        String refresh = registered.path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isOk());

        JsonNode afterLogout = postJson("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(refresh));
        assertThat(afterLogout.path("code").asInt()).isEqualTo(10105);
    }

    // ---------------------------------------------------------------- 用户资料

    @Test
    @DisplayName("他人主页免登录可读")
    void userDetail_isPubliclyReadable() throws Exception {
        JsonNode registered = register("alice08");
        long userId = registered.path("data").path("user").path("id").asLong();

        MvcResult result = mockMvc.perform(get("/api/v1/users/" + userId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("data").path("user").path("username").asText()).isEqualTo("alice08");
        assertThat(body.path("data").path("stat").path("bookshelfCount").asInt()).isZero();
    }

    @Test
    @DisplayName("修改自己的资料：只改传了的字段")
    void updateProfile_onlyTouchesProvidedFields() throws Exception {
        JsonNode registered = register("alice09");
        String access = registered.path("data").path("accessToken").asText();

        MvcResult updated = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"书虫","bio":"只看完结"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(updated);
        assertThat(body.path("data").path("nickname").asText()).isEqualTo("书虫");
        assertThat(body.path("data").path("bio").asText()).isEqualTo("只看完结");
    }

    @Test
    @DisplayName("PUT /users/me 传空对象：不报 500，也不改任何字段")
    void updateProfile_emptyBody_isNoop() throws Exception {
        JsonNode registered = register("alice11");
        String access = registered.path("data").path("accessToken").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        JsonNode me = readJson(mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)).andReturn());
        assertThat(me.path("data").path("nickname").asText()).isEqualTo("昵称alice11");
    }

    // ---------------------------------------------------------------- helpers

    private String registerPayload(String username) {
        return """
                {"username":"%s","password":"pwd123456","nickname":"昵称%s"}
                """.formatted(username, username);
    }

    private JsonNode register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(username)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result);
    }

    private JsonNode postJson(String url, String payload) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }
}
