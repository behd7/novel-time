package com.novel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.rank.application.RankRefreshService;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 榜单链路端到端：真实行为 → 结算 → 读接口。
 *
 * <p>这条链路只有在真库 + 真 Redis 上跑才有意义，因为它要验证的正是"三处分散的记账
 * 最后能汇成一张榜"：详情页的一次访问、收藏接口的一次插入、以及结算任务的一次扫描，
 * 三者分属不同的层，中间任何一处漏掉，单测都看不出来——单测里每个端口都是被 Mock 调用的。
 *
 * <p>定时结算在测试里关掉（{@code novel.rank.enabled=false}），由用例手动触发：
 * 后台任务在断言之间跑一轮，会让"刚加了收藏但榜还没变"和"榜已经变了"两种状态混在一起。
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RankFlowIntegrationTest {

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
        // 阅读数落库会每秒改写 view_count，而总榜正是按它算的，必须关掉
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
    private NovelRepository novelRepository;
    @Autowired
    private RankRefreshService rankRefreshService;

    // ------------------------------------------------------------------ 日榜

    @Test
    @DisplayName("阅读和收藏都会进日榜，没人碰过的书不出现")
    void dailyRank_reflectsRealBehavior() throws Exception {
        long quiet = createNovel("无人问津");
        long read = createNovel("被读过的");
        long loved = createNovel("被读又被收藏的");

        readNovel(read);
        readNovel(loved);
        favorite(loved, "rankfan01");

        rankRefreshService.refresh(today());
        Map<Long, Integer> ranks = ranksOf("daily");

        assertThat(ranks).containsKey(read).containsKey(loved);
        assertThat(ranks).doesNotContainKey(quiet);
        assertThat(ranks.get(loved)).isLessThan(ranks.get(read));
    }

    @Test
    @DisplayName("同一个人同一天反复打开详情页，榜单只计一次")
    void repeatedView_countsOnce() throws Exception {
        long novelId = createNovel("只算一次");

        readNovel(novelId);
        readNovel(novelId);
        readNovel(novelId);

        rankRefreshService.refresh(today());

        // raw = 1 → log10(2) = 0.3010；若计了三次会是 log10(4) = 0.6021
        assertThat(scoreOf("daily", novelId)).isCloseTo(0.3010, offset(0.0005));
    }

    // ------------------------------------------------------------------ 总榜

    @Test
    @DisplayName("总榜读累计阅读数，并顺手把热度分写回 novel.hot_score")
    void allRank_usesTotalsAndWritesHotScore() throws Exception {
        long hot = createNovel("累计阅读很多");
        long cold = createNovel("累计阅读很少");
        novelRepository.addViewCount(hot, 500);
        novelRepository.addViewCount(cold, 1);

        rankRefreshService.refresh(today());
        Map<Long, Integer> ranks = ranksOf("all");

        assertThat(ranks.get(hot)).isLessThan(ranks.get(cold));

        Novel reloaded = novelRepository.findById(hot).orElseThrow();
        assertThat(reloaded.hotScore()).isNotNull();
        assertThat(reloaded.hotScore().doubleValue()).isGreaterThan(0d);
    }

    // ------------------------------------------------------------------ 契约

    @Test
    @DisplayName("榜单免登录可读，字段带名次、分数和小说卡片")
    void rank_isPublicAndCarriesCard() throws Exception {
        long novelId = createNovel("公开可读");
        readNovel(novelId);
        rankRefreshService.refresh(today());

        JsonNode list = rankList("weekly");
        assertThat(list.size()).isGreaterThanOrEqualTo(1);
        JsonNode first = list.get(0);
        assertThat(first.path("rankNo").asInt()).isEqualTo(1);
        assertThat(first.path("score").asDouble()).isGreaterThan(0d);
        assertThat(first.path("novel").path("title").asText()).isNotBlank();
        assertThat(first.path("novel").has("content")).isFalse();
    }

    @Test
    @DisplayName("刷新两次得到同一份榜：结算必须幂等")
    void refresh_isIdempotent() throws Exception {
        long novelId = createNovel("幂等检查");
        readNovel(novelId);

        rankRefreshService.refresh(today());
        String first = rankList("daily").toString();
        rankRefreshService.refresh(today());

        assertThat(rankList("daily").toString()).isEqualTo(first);
    }

    @Test
    @DisplayName("非法周期返回 400，不走 200 + 业务码")
    void invalidPeriod_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/ranks").param("period", "hourly"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("翻过最后一页返回空列表，total 仍是快照条数")
    void beyondLastPage_returnsEmptyList() throws Exception {
        long novelId = createNovel("翻页越界");
        readNovel(novelId);
        rankRefreshService.refresh(today());

        JsonNode body = readJson(mockMvc.perform(get("/api/v1/ranks")
                        .param("period", "daily").param("page", "99").param("size", "50"))
                .andExpect(status().isOk()).andReturn());

        assertThat(body.path("data").path("list")).isEmpty();
        assertThat(body.path("data").path("total").asInt()).isGreaterThanOrEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private java.time.LocalDate today() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    }

    private long createNovel(String title) {
        long id = novelRepository.create(
                new NovelRepository.NewNovel(title, "榜单作者", null, 1L, Novel.STATUS_ONGOING, "简介"));
        novelRepository.updateStats(id, 1000, 3, 0L, LocalDateTime.now().minusDays(1));
        return id;
    }

    private void readNovel(long novelId) throws Exception {
        mockMvc.perform(get("/api/v1/novels/" + novelId)).andExpect(status().isOk());
    }

    private void favorite(long novelId, String username) throws Exception {
        String token = registerAndLogin(username);
        mockMvc.perform(post("/api/v1/bookshelf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novelId\":" + novelId + "}"))
                .andExpect(status().isOk());
    }

    private Map<Long, Integer> ranksOf(String period) throws Exception {
        Map<Long, Integer> ranks = new HashMap<>();
        for (JsonNode item : rankList(period)) {
            ranks.put(item.path("novel").path("id").asLong(), item.path("rankNo").asInt());
        }
        return ranks;
    }

    private double scoreOf(String period, long novelId) throws Exception {
        for (JsonNode item : rankList(period)) {
            if (item.path("novel").path("id").asLong() == novelId) {
                return item.path("score").asDouble();
            }
        }
        throw new AssertionError("榜单里没有 novelId=" + novelId);
    }

    private JsonNode rankList(String period) throws Exception {
        JsonNode body = readJson(mockMvc.perform(get("/api/v1/ranks")
                        .param("period", period).param("size", "50"))
                .andExpect(status().isOk()).andReturn());
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("list");
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

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
