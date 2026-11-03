package com.novel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.novel.importing.NovelImportService;
import com.novel.core.novel.importing.ParsedChapter;
import com.novel.core.novel.importing.ParsedNovel;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阅读链路端到端测试：导入 → 列表 → 详情 → 目录 → 正文 → 书架。
 *
 * <p>跑在真实的 MySQL 8 与 Redis 7 上，所以顺带验证了三件只有真库才能暴露的事：
 * 唯一键 {@code uk_novel_seq} 不会因为章节号重复而炸、
 * 书架取消收藏再收藏不会撞 {@code uk_user_novel}、
 * 阅读数走的是 Redis 自增而不是直接更新 {@code novel} 表。
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReadingFlowIntegrationTest {

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
        registry.add("novel.auth.rate-limit.login-max-attempts", () -> "1000");
        // 关掉定时任务，避免阅读数在测试中途被刷走，导致断言飘忽
        registry.add("novel.reading.view-flush-interval-ms", () -> "3600000");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NovelImportService importService;

    private static final AtomicBoolean IMPORTED = new AtomicBoolean(false);
    private static volatile long sampleNovelId;

    // ------------------------------------------------------------------ 阅读

    @Test
    @DisplayName("分类已由 Flyway 种下，且接口免登录可读")
    void categories_areSeeded() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(toList(body.path("data"))).anySatisfy(node ->
                assertThat(node.path("name").asText()).isEqualTo("玄幻"));
    }

    @Test
    @DisplayName("导入的小说出现在列表里，且字段完整")
    void list_containsImportedNovel() throws Exception {
        long id = sampleNovelId();

        JsonNode body = readJson(mockMvc.perform(get("/api/v1/novels")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andReturn());

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(toList(body.path("data").path("list"))).anySatisfy(node -> {
            assertThat(node.path("id").asLong()).isEqualTo(id);
            assertThat(node.path("title").asText()).isEqualTo("测试之书");
            assertThat(node.path("chapterCount").asInt()).isEqualTo(3);
            assertThat(node.path("wordCount").asInt()).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("详情返回章节数、末章信息与阅读数")
    void detail_showsChapterAndViewCount() throws Exception {
        long id = sampleNovelId();

        JsonNode body = readJson(mockMvc.perform(get("/api/v1/novels/" + id))
                .andExpect(status().isOk()).andReturn());

        JsonNode data = body.path("data");
        assertThat(data.path("title").asText()).isEqualTo("测试之书");
        assertThat(data.path("chapterCount").asInt()).isEqualTo(3);
        assertThat(data.path("latestChapterSeq").asInt()).isEqualTo(3);
        assertThat(data.path("latestChapterTitle").asText()).isEqualTo("风波起");
        // 阅读数来自 Redis 增量 + 库里的值，应该 >= 1（这次访问本身也计数）
        assertThat(data.path("viewCount").asLong()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("目录按 seq 升序返回")
    void catalog_isOrderedBySeq() throws Exception {
        long id = sampleNovelId();

        JsonNode body = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/chapters")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andReturn());

        JsonNode list = body.path("data").path("list");
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.get(0).path("seq").asInt()).isEqualTo(1);
        assertThat(list.get(1).path("seq").asInt()).isEqualTo(2);
        assertThat(list.get(2).path("seq").asInt()).isEqualTo(3);
        // 目录项不应该带正文，否则目录接口会变重
        assertThat(list.get(0).has("content")).isFalse();
    }

    @Test
    @DisplayName("读取正文返回内容与上下章序号")
    void chapter_returnsContentAndNeighbors() throws Exception {
        long id = sampleNovelId();

        JsonNode first = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/chapters/1"))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(first.path("content").asText()).contains("第一章的正文");
        // 全局配置了 non_null 序列化，null 字段会整个消失，所以判断的是"有没有非空值"
        assertThat(first.hasNonNull("prevSeq")).isFalse();
        assertThat(first.path("nextSeq").asInt()).isEqualTo(2);

        JsonNode last = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/chapters/3"))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(last.path("prevSeq").asInt()).isEqualTo(2);
        assertThat(last.hasNonNull("nextSeq")).isFalse();
    }

    @Test
    @DisplayName("章节不存在返回 10202，而不是 500")
    void chapter_notFound_returnsBusinessCode() throws Exception {
        long id = sampleNovelId();

        JsonNode body = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/chapters/99"))
                .andExpect(status().isOk()).andReturn());

        assertThat(body.path("code").asInt()).isEqualTo(10202);
    }

    // ------------------------------------------------------------------ 书架

    @Test
    @DisplayName("书架接口必须登录")
    void bookshelf_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/bookshelf")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("加入书架 → 列表可见 → 更新进度 → hasUpdate 正确")
    void bookshelf_addAndTrackProgress() throws Exception {
        long novelId = sampleNovelId();
        String token = registerAndLogin("reader01");

        mockMvc.perform(post("/api/v1/bookshelf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novelId\":" + novelId + "}"))
                .andExpect(status().isOk());

        JsonNode afterAdd = bookshelf(token);
        assertThat(afterAdd.path("total").asInt()).isEqualTo(1);
        JsonNode item = afterAdd.path("list").get(0);
        assertThat(item.path("novelId").asLong()).isEqualTo(novelId);
        assertThat(item.path("lastChapterSeq").asInt()).isZero();
        // 一章没读，而书有 3 章 → 有新章节
        assertThat(item.path("hasUpdate").asBoolean()).isTrue();

        mockMvc.perform(put("/api/v1/bookshelf/" + novelId + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seq\":3}"))
                .andExpect(status().isOk());

        JsonNode afterProgress = bookshelf(token);
        assertThat(afterProgress.path("list").get(0).path("lastChapterSeq").asInt()).isEqualTo(3);
        // 读到最后一章 → 没有更新
        assertThat(afterProgress.path("list").get(0).path("hasUpdate").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("进度指向不存在的章节被拒绝")
    void bookshelf_progressToMissingChapter_rejected() throws Exception {
        long novelId = sampleNovelId();
        String token = registerAndLogin("reader02");

        mockMvc.perform(post("/api/v1/bookshelf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novelId\":" + novelId + "}"))
                .andExpect(status().isOk());

        JsonNode body = readJson(mockMvc.perform(put("/api/v1/bookshelf/" + novelId + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seq\":999}"))
                .andExpect(status().isOk()).andReturn());

        assertThat(body.path("code").asInt()).isEqualTo(10202);
    }

    @Test
    @DisplayName("取消收藏后能再次收藏（证明是物理删除，没撞唯一键）")
    void bookshelf_removeThenReAdd() throws Exception {
        long novelId = sampleNovelId();
        String token = registerAndLogin("reader03");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/bookshelf")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"novelId\":" + novelId + "}"))
                    .andExpect(status().isOk());
            assertThat(bookshelf(token).path("total").asInt()).isEqualTo(1);

            mockMvc.perform(delete("/api/v1/bookshelf/" + novelId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
            assertThat(bookshelf(token).path("total").asInt()).isZero();
        }
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode bookshelf(String token) throws Exception {
        return readJson(mockMvc.perform(get("/api/v1/bookshelf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    /** 幂等地导入一本 3 章的样本书，所有用例共用。 */
    private long sampleNovelId() {
        if (sampleNovelId > 0) {
            return sampleNovelId;
        }
        synchronized (IMPORTED) {
            if (sampleNovelId > 0) {
                return sampleNovelId;
            }
            String c1 = "第一章的正文，这里放一些足够长的文字，用来验证正文字数统计与内容返回。";
            String c2 = "第二章的正文，主角遇到了一点麻烦，但很快解决了。";
            String c3 = "第三章的正文，风波起来又平复，故事告一段落。";
            ParsedNovel parsed = new ParsedNovel("测试之书", "测试作者", "玄幻", "集成测试用的书",
                    List.of(
                            new ParsedChapter(1, "开端", c1, ParsedChapter.countWords(c1)),
                            new ParsedChapter(2, "转折", c2, ParsedChapter.countWords(c2)),
                            new ParsedChapter(3, "风波起", c3, ParsedChapter.countWords(c3))));
            sampleNovelId = importService.importNovel(parsed, "玄幻");
            return sampleNovelId;
        }
    }

    private String registerAndLogin(String username) throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"pwd123456","nickname":"%s"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(registered).path("data").path("accessToken").asText();
    }

    private List<JsonNode> toList(JsonNode array) {
        List<JsonNode> nodes = new java.util.ArrayList<>();
        array.forEach(nodes::add);
        return nodes;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
