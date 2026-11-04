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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评分与书评端到端测试。
 *
 * <p>跑在真实 MySQL 与 Redis 上，专门验证三类"只有真库才会暴露"的行为：
 * 增量聚合在多人打分后是否准、唯一键能否挡住重复点赞、软删除后列表还能不能正常过滤。
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReviewFlowIntegrationTest {

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
        registry.add("novel.reading.view-flush-interval-ms", () -> "3600000");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NovelImportService importService;

    /** 每个用例一本书，评分聚合断言才不会互相污染。 */
    private static final java.util.concurrent.atomic.AtomicInteger NOVEL_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    // ------------------------------------------------------------------ 评分

    @Test
    @DisplayName("三个用户打分：平均分与人数正确，星级分布完整")
    void rating_aggregatesAcrossUsers() throws Exception {
        long id = newNovel();
        String a = register("rater1");
        String b = register("rater2");
        String c = register("rater3");

        rate(id, a, 5);
        rate(id, b, 4);
        rate(id, c, 3);

        JsonNode summary = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/rating"))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(summary.path("ratingCount").asInt()).isEqualTo(3);
        assertThat(summary.path("avgRating").asDouble()).isEqualTo(4.0);
        assertThat(summary.path("distribution").path("5").asInt()).isEqualTo(1);
        assertThat(summary.path("distribution").path("3").asInt()).isEqualTo(1);
        // 未登录访问不该看到 myScore
        assertThat(summary.hasNonNull("myScore")).isFalse();
    }

    @Test
    @DisplayName("改分不增加人数，只调整总分")
    void rating_changeKeepsCount() throws Exception {
        long id = newNovel();
        String a = register("changer1");
        String b = register("changer2");

        rate(id, a, 5);
        rate(id, b, 5);
        JsonNode afterChange = rate(id, a, 1);

        assertThat(afterChange.path("ratingCount").asInt()).isEqualTo(2);
        // (1 + 5) / 2 = 3
        assertThat(afterChange.path("avgRating").asDouble()).isEqualTo(3.0);
        assertThat(afterChange.path("myScore").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("打分需要登录")
    void rating_requiresAuth() throws Exception {
        mockMvc.perform(put("/api/v1/novels/" + newNovel() + "/rating")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("发书评时带的星级会一并记入评分")
    void review_withScore_alsoRates() throws Exception {
        long id = newNovel();
        String token = register("reviewRater");

        int before = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/rating"))
                .andReturn()).path("data").path("ratingCount").asInt();

        postJson("/api/v1/novels/" + id + "/reviews", token, """
                {"content":"带星留言","score":5}""", 201);

        // myScore 只在带令牌时返回，所以这里必须带上
        JsonNode summary = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/rating")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()).path("data");
        assertThat(summary.path("ratingCount").asInt()).isEqualTo(before + 1);
        assertThat(summary.path("myScore").asInt()).isEqualTo(5);
    }

    // ------------------------------------------------------------------ 书评

    @Test
    @DisplayName("最热排序：被点赞的书评排到前面")
    void reviews_hotSortPutsLikedFirst() throws Exception {
        long id = newNovel();
        String author = register("authorHot");
        String fan = register("fanHot");

        postJson("/api/v1/novels/" + id + "/reviews", author, """
                {"content":"第一条"}""", 201);
        long secondReview = postJson("/api/v1/novels/" + id + "/reviews", author, """
                {"content":"第二条"}""", 201).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/reviews/" + secondReview + "/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan))
                .andExpect(status().isOk());

        JsonNode hot = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/reviews")
                .param("sort", "hot")).andExpect(status().isOk()).andReturn()).path("data").path("list");

        assertThat(hot.get(0).path("id").asLong()).isEqualTo(secondReview);
        assertThat(hot.get(0).path("likeCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("只能删自己的书评，删别人的返回 10403")
    void reviews_deleteOwnOnly() throws Exception {
        long id = newNovel();
        String owner = register("owner1");
        String stranger = register("stranger1");

        long reviewId = postJson("/api/v1/novels/" + id + "/reviews", owner, """
                {"content":"我写的"}""", 201).path("data").path("id").asLong();

        JsonNode denied = readJson(mockMvc.perform(delete("/api/v1/reviews/" + reviewId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isOk()).andReturn());
        assertThat(denied.path("code").asInt()).isEqualTo(10403);

        mockMvc.perform(delete("/api/v1/reviews/" + reviewId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());

        // 软删除后不应再出现在列表里
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/reviews"))
                .andReturn()).path("data").path("list");
        assertThat(list).noneMatch(node -> node.path("id").asLong() == reviewId);
    }

    // ------------------------------------------------------------------ 楼中楼

    @Test
    @DisplayName("楼中楼：回复的回复归到同一 root，并带出被回复者昵称")
    void replies_twoLevels() throws Exception {
        long id = newNovel();
        String a = register("threadA");
        String b = register("threadB");
        String c = register("threadC");

        long reviewId = postJson("/api/v1/novels/" + id + "/reviews", a, """
                {"content":"主楼"}""", 201).path("data").path("id").asLong();

        JsonNode first = postJson("/api/v1/reviews/" + reviewId + "/replies", b, """
                {"content":"一楼"}""", 201).path("data");
        long firstId = first.path("id").asLong();
        assertThat(first.hasNonNull("parentReplyId")).isFalse();

        JsonNode second = postJson("/api/v1/reviews/" + reviewId + "/replies", c, """
                {"content":"回复一楼","parentReplyId":%d}""".formatted(firstId), 201).path("data");
        assertThat(second.path("parentReplyId").asLong()).isEqualTo(firstId);
        assertThat(second.path("parentNickname").asText()).isEqualTo("threadB");

        JsonNode replies = readJson(mockMvc.perform(get("/api/v1/reviews/" + reviewId + "/replies"))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(replies.path("total").asInt()).isEqualTo(2);

        JsonNode review = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/reviews"))
                .andReturn()).path("data").path("list").get(0);
        assertThat(review.path("replyCount").asInt()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ 点赞

    @Test
    @DisplayName("点赞幂等：连点两次计数只加一，取消后再点仍然生效")
    void like_isIdempotentAndReversible() throws Exception {
        long id = newNovel();
        String author = register("likeAuthor");
        String fan = register("likeFan");

        long reviewId = postJson("/api/v1/novels/" + id + "/reviews", author, """
                {"content":"求赞"}""", 201).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());

        assertThat(likeCountOf(id, reviewId, fan)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/reviews/" + reviewId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());
        assertThat(likeCountOf(id, reviewId, fan)).isZero();

        // 取消是物理删除，所以再点一次不会被唯一键挡住
        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());
        assertThat(likeCountOf(id, reviewId, fan)).isEqualTo(1);
    }

    @Test
    @DisplayName("列表里的 liked 标记按当前登录人计算，匿名访问恒为 false")
    void liked_flagDependsOnViewer() throws Exception {
        long id = newNovel();
        String author = register("flagAuthor");
        String fan = register("flagFan");

        long reviewId = postJson("/api/v1/novels/" + id + "/reviews", author, """
                {"content":"标记测试"}""", 201).path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());

        JsonNode asFan = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan))
                .andReturn()).path("data").path("list");
        JsonNode asAnonymous = readJson(mockMvc.perform(get("/api/v1/novels/" + id + "/reviews"))
                .andReturn()).path("data").path("list");

        assertThat(findById(asFan, reviewId).path("liked").asBoolean()).isTrue();
        assertThat(findById(asAnonymous, reviewId).path("liked").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode findById(JsonNode list, long id) {
        for (JsonNode node : list) {
            if (node.path("id").asLong() == id) {
                return node;
            }
        }
        throw new AssertionError("列表里没有 id=" + id);
    }

    private int likeCountOf(long novelId, long reviewId, String token) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/novels/" + novelId + "/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()).path("data").path("list");
        return findById(list, reviewId).path("likeCount").asInt();
    }

    private JsonNode rate(long novelId, String token, int score) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/novels/" + novelId + "/rating")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":" + score + "}"))
                .andExpect(status().isOk()).andReturn();
        return readJson(result).path("data");
    }

    private JsonNode postJson(String url, String token, String body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = post(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return readJson(mockMvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 导入一本全新的书：同名会被导入服务跳过，所以标题里带序号。 */
    private long newNovel() {
        int seq = NOVEL_SEQ.incrementAndGet();
        String content = "用于评分与书评测试的正文，长度够数几个字。";
        ParsedNovel parsed = new ParsedNovel("评论测试之书-" + seq, "测试作者", "玄幻", "评论测试",
                List.of(new ParsedChapter(1, "第一章", content, ParsedChapter.countWords(content))));
        return importService.importNovel(parsed, "玄幻");
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"pwd123456","nickname":"%s"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).path("data").path("accessToken").asText();
    }
}
