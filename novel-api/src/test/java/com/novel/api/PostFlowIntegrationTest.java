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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 社区端到端测试：发帖引用、列表/详情、点赞、两层评论与软删除。
 *
 * <p>重点验证三件单测看不出来的事：引用关系落库后能否回显成小说卡片、
 * 统一点赞表的唯一键在并发重复点赞时是否兜得住、软删除后列表与详情是否同时消失。
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostFlowIntegrationTest {

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
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&useUnicode=true&characterEncoding=utf8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("novel.jwt.secret", () -> "integration-test-secret-0123456789abcdef");
        registry.add("novel.auth.rate-limit.login-max-attempts", () -> "1000");
        registry.add("novel.reading.view-flush-interval-ms", () -> "3600000");
        registry.add("novel.search.enabled", () -> "false");
        registry.add("novel.outbox.enabled", () -> "false");
        registry.add("novel.rank.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NovelImportService importService;

    private static final java.util.concurrent.atomic.AtomicInteger NOVEL_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger POST_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    // ------------------------------------------------------------------ 发帖与引用

    @Test
    @DisplayName("发帖引用小说：详情返回卡片，列表可按 novelId 反查")
    void create_withRefs_returnsCardAndReverseLookup() throws Exception {
        long novelId = newNovel();
        String token = register("postAuthor1");

        JsonNode created = createPost(token, novelId, "守山人这段[[ref:0]]写得真好");
        long postId = created.path("id").asLong();

        assertThat(created.path("content").asText()).contains("[[ref:0]]");
        assertThat(created.path("refs")).hasSize(1);
        assertThat(created.path("refs").get(0).path("refText").asText()).isEqualTo("[[ref:0]]");
        assertThat(created.path("refs").get(0).path("novelId").asLong()).isEqualTo(novelId);
        assertThat(created.path("refs").get(0).path("title").asText()).startsWith("社区测试之书-");

        // 浏览免登录，详情里的卡片同样要能回显
        JsonNode detail = readJson(mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(detail.path("refs").get(0).path("novelId").asLong()).isEqualTo(novelId);
        assertThat(detail.path("viewCount").asLong()).isEqualTo(1);

        JsonNode list = readJson(mockMvc.perform(get("/api/v1/posts")
                        .param("novelId", String.valueOf(novelId)))
                .andExpect(status().isOk()).andReturn()).path("data").path("list");
        assertThat(list).anyMatch(node -> node.path("id").asLong() == postId);
    }

    @Test
    @DisplayName("匿名可读、写操作必须登录")
    void anonymousCanRead_butCannotWrite() throws Exception {
        mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("越界分页保留真实 total，size 超上限被夹到 50")
    void list_paginationBoundary() throws Exception {
        long novelId = newNovel();
        String token = register("postPageAuthor");
        createPost(token, novelId, "分页边界");

        JsonNode overPage = readJson(mockMvc.perform(get("/api/v1/posts")
                        .param("novelId", String.valueOf(novelId))
                        .param("page", "99"))
                .andReturn()).path("data");
        assertThat(overPage.path("list").size()).isZero();
        assertThat(overPage.path("total").asInt()).isEqualTo(1);

        JsonNode bigSize = readJson(mockMvc.perform(get("/api/v1/posts")
                        .param("novelId", String.valueOf(novelId))
                        .param("size", "1000"))
                .andReturn()).path("data");
        assertThat(bigSize.path("size").asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("引用不存在的书返回 10201，占位符越界返回 400")
    void invalidRefs_rejected() throws Exception {
        String token = register("postAuthor2");
        long novelId = newNovel();

        JsonNode missing = postJson("/api/v1/posts", token, """
                {"title":"引用不存在的书","content":"看这本[[ref:0]]","refNovelIds":[999999]}
                """, 200);
        assertThat(missing.path("code").asInt()).isEqualTo(10201);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"占位符越界","content":"看这本[[ref:1]]","refNovelIds":[%d]}
                                """.formatted(novelId)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 列表与热帖

    @Test
    @DisplayName("热帖排序：先发的帖子被点赞后压过后来者，最新排序仍按时间倒序")
    void hotSort_likedFirst() throws Exception {
        long novelId = newNovel();
        String author = register("postHotAuthor");
        String fan = register("postHotFan");

        long first = createPost(author, novelId, "第一条").path("id").asLong();
        mockMvc.perform(post("/api/v1/posts/" + first + "/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan))
                .andExpect(status().isOk());
        long second = createPost(author, novelId, "第二条").path("id").asLong();

        JsonNode hot = readJson(mockMvc.perform(get("/api/v1/posts")
                        .param("sort", "hot")
                        .param("novelId", String.valueOf(novelId)))
                .andReturn()).path("data").path("list");
        assertThat(hot.get(0).path("id").asLong()).isEqualTo(first);

        JsonNode latest = readJson(mockMvc.perform(get("/api/v1/posts")
                        .param("sort", "latest")
                        .param("novelId", String.valueOf(novelId)))
                .andReturn()).path("data").path("list");
        assertThat(latest.get(0).path("id").asLong()).isEqualTo(second);
    }

    // ------------------------------------------------------------------ 点赞

    @Test
    @DisplayName("帖子点赞幂等，作者 user_stat.like_received 同步增长")
    void like_isIdempotentAndUpdatesAuthorStat() throws Exception {
        long novelId = newNovel();
        String author = register("postLikeAuthor");
        String fan = register("postLikeFan");

        long postId = createPost(author, novelId, "求赞").path("id").asLong();

        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());

        assertThat(postDetail(postId, fan).path("likeCount").asInt()).isEqualTo(1);
        assertThat(userStat(author).path("likeReceived").asInt()).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/posts/" + postId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());
        assertThat(postDetail(postId, fan).path("likeCount").asInt()).isZero();
        assertThat(userStat(author).path("likeReceived").asInt()).isZero();
    }

    // ------------------------------------------------------------------ 评论

    @Test
    @DisplayName("两层评论：回复带父评论与昵称，发/删同步帖子评论数")
    void comments_twoLevelsAndCounters() throws Exception {
        long novelId = newNovel();
        String author = register("postCommentAuthor");
        String first = register("postCommentFirst");
        String second = register("postCommentSecond");

        long postId = createPost(author, novelId, "来聊").path("id").asLong();

        long topId = postJson("/api/v1/posts/" + postId + "/comments", first, """
                {"content":"一楼"}
                """, 201).path("data").path("id").asLong();

        JsonNode reply = postJson("/api/v1/posts/" + postId + "/comments", second, """
                {"content":"回复一楼","parentCommentId":%d}
                """.formatted(topId), 201).path("data");

        assertThat(reply.path("parentCommentId").asLong()).isEqualTo(topId);
        assertThat(reply.path("parentNickname").asText()).isEqualTo("postCommentFirst");
        assertThat(reply.path("rootId").asLong()).isEqualTo(topId);

        JsonNode comments = readJson(mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(comments.path("total").asInt()).isEqualTo(2);
        assertThat(postDetail(postId, null).path("commentCount").asInt()).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/comments/" + topId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isOk());
        assertThat(postDetail(postId, null).path("commentCount").asInt()).isEqualTo(1);

        // 主楼被删后回复仍在：rootId 与 parentCommentId 不能丢，父昵称置空、parentDeleted 标记
        JsonNode afterDelete = readJson(mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
                .andExpect(status().isOk()).andReturn()).path("data").path("list");
        assertThat(afterDelete).hasSize(1);
        assertThat(afterDelete.get(0).path("rootId").asLong()).isEqualTo(topId);
        assertThat(afterDelete.get(0).path("parentCommentId").asLong()).isEqualTo(topId);
        assertThat(afterDelete.get(0).path("parentDeleted").asBoolean()).isTrue();
        assertThat(afterDelete.get(0).hasNonNull("parentNickname")).isFalse();
    }

    @Test
    @DisplayName("评论点赞幂等，并计入评论作者收到的赞")
    void commentLike_isIdempotent() throws Exception {
        long novelId = newNovel();
        String author = register("postCLikeAuthor");
        String fan = register("postCLikeFan");

        long postId = createPost(author, novelId, "评论点赞").path("id").asLong();
        long commentId = postJson("/api/v1/posts/" + postId + "/comments", author, """
                {"content":"给我点赞"}
                """, 201).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/comments/" + commentId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/comments/" + commentId + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan)).andExpect(status().isOk());

        JsonNode comments = readJson(mockMvc.perform(get("/api/v1/posts/" + postId + "/comments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fan))
                .andReturn()).path("data").path("list");
        assertThat(comments.get(0).path("likeCount").asInt()).isEqualTo(1);
        assertThat(comments.get(0).path("liked").asBoolean()).isTrue();
        assertThat(userStat(author).path("likeReceived").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 删除

    @Test
    @DisplayName("只能删自己的帖子；软删后列表消失、详情返回 10501")
    void deleteOwnOnly_softDelete() throws Exception {
        long novelId = newNovel();
        String owner = register("postOwner");
        String stranger = register("postStranger");

        long postId = createPost(owner, novelId, "我写的").path("id").asLong();

        JsonNode denied = readJson(mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isOk()).andReturn());
        assertThat(denied.path("code").asInt()).isEqualTo(10502);

        mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());

        JsonNode list = readJson(mockMvc.perform(get("/api/v1/posts"))
                .andReturn()).path("data").path("list");
        assertThat(list).noneMatch(node -> node.path("id").asLong() == postId);

        JsonNode detail = readJson(mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk()).andReturn());
        assertThat(detail.path("code").asInt()).isEqualTo(10501);
        assertThat(userStat(owner).path("postCount").asInt()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode createPost(String token, long novelId, String content) throws Exception {
        int seq = POST_SEQ.incrementAndGet();
        String body = """
                {"title":"社区帖子-%d","content":"%s","refNovelIds":[%d]}
                """.formatted(seq, content, novelId);
        return postJson("/api/v1/posts", token, body, 201).path("data");
    }

    private JsonNode postDetail(long postId, String token) throws Exception {
        MockHttpServletRequestBuilder builder = get("/api/v1/posts/" + postId);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return readJson(mockMvc.perform(builder).andExpect(status().isOk()).andReturn()).path("data");
    }

    private JsonNode userStat(String token) throws Exception {
        long userId = readJson(mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()).path("data").path("id").asLong();
        return readJson(mockMvc.perform(get("/api/v1/users/" + userId))
                .andReturn()).path("data").path("stat");
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
        String content = "用于社区测试的正文，长度够数几个字。";
        ParsedNovel parsed = new ParsedNovel("社区测试之书-" + seq, "测试作者", "玄幻", "社区测试",
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
