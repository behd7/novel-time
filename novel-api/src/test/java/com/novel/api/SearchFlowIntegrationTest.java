package com.novel.api;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.api.SearchFlowIntegrationTest.FakeBroker;
import com.novel.core.novel.importing.NovelImportService;
import com.novel.core.novel.importing.ParsedChapter;
import com.novel.core.novel.importing.ParsedNovel;
import com.novel.infra.event.OutboxMessageSender;
import com.novel.infra.event.OutboxRelay;
import com.novel.infra.mq.NovelSearchSyncHandler;
import com.novel.infra.search.NovelIndexRebuildJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 搜索链路端到端：写库 → Outbox → 投递 → 消费 → 可搜。
 *
 * <p>用真实的 MySQL + Redis + ES 容器，把 MQ 换成一个"直连消费者"的假 broker——
 * AMQP 那一跳很薄（发一条消息、等确认），真正容易出错的是"文档写进去对不对、
 * 高亮有没有、重建切别名后还能不能搜"。把容器的启动成本花在后者上更值。
 *
 * <p>分词器用 {@code standard}：官方 ES 镜像不带 IK，测试里用 IK 会直接建不出索引。
 * IK 的分词效果只能拿带插件的镜像人工验（见 README 的验证步骤）。
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeBroker.class)
class SearchFlowIntegrationTest {

    private static final String ALIAS = "novel";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("novel")
            .withUsername("novel")
            .withPassword("novel");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> ES = new GenericContainer<>(DockerImageName.parse("elasticsearch:8.13.0"))
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.ml.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(Wait.forHttp("/").forPort(9200).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&useUnicode=true&characterEncoding=utf8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + ES.getHost() + ":" + ES.getMappedPort(9200));
        // 官方镜像没有 IK，显式退化；生产用 ik_max_word
        registry.add("novel.search.analyzer", () -> "standard");
        registry.add("novel.search.search-analyzer", () -> "standard");
        registry.add("novel.jwt.secret", () -> "integration-test-secret-0123456789abcdef");
        registry.add("novel.auth.rate-limit.login-max-attempts", () -> "1000");
        registry.add("novel.reading.view-flush-interval-ms", () -> "3600000");
        // 关掉真监听器与定时投递：测试里手动调 relayOnce，结果才是确定的
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("novel.outbox.poll-interval-ms", () -> "3600000");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NovelImportService importService;
    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private NovelIndexRebuildJob rebuildJob;
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("导入一本新书：一轮投递后即可搜到，带高亮，并留下消费流水")
    void importedNovel_becomesSearchable() throws Exception {
        long novelId = importNovel("青云志", "一个守山人捡到半卷残破剑谱，从此山下的江湖与山上的云海都不再安分。");

        assertThat(outboxRelay.relayOnce()).isGreaterThanOrEqualTo(1);

        JsonNode hit = awaitHit("青云志", novelId);
        assertThat(hit).as("导入的书应该能被搜到").isNotNull();
        assertThat(hit.path("title").asText()).isEqualTo("青云志");
        assertThat(hit.path("categoryName").asText()).isEqualTo("玄幻");
        assertThat(hit.path("highlight").path("title").asText()).contains("<em>");

        assertThat(count("SELECT COUNT(*) FROM outbox WHERE status = 1 AND aggregate_id = ?", novelId))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM mq_consume_log WHERE consumer = 'search-sync'"))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("评分变化也会同步进索引，搜索结果里的平均分不会落后一天")
    void ratingChange_updatesIndex() throws Exception {
        long novelId = importNovel("评分同步之书", "这本书用来验证评分能不能同步到索引里。");
        outboxRelay.relayOnce();
        assertThat(awaitHit("评分同步之书", novelId).path("avgRating").asDouble()).isZero();

        String token = register("search_rater");
        mockMvc.perform(put("/api/v1/novels/" + novelId + "/rating")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}"))
                .andExpect(status().isOk());

        assertThat(outboxRelay.relayOnce()).isGreaterThanOrEqualTo(1);
        JsonNode hit = awaitHitWithRating(novelId, 5.0);
        assertThat(hit).as("评分应该同步进索引").isNotNull();
        assertThat(hit.path("ratingCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("搜索建议能按书名前缀补全")
    void suggest_completesByPrefix() throws Exception {
        importNovel("补全测试青云录", "用于验证搜索建议。");
        outboxRelay.relayOnce();

        JsonNode data = awaitSuggest("补全");
        assertThat(data).isNotEmpty();
        assertThat(data.get(0).path("title").asText()).isEqualTo("补全测试青云录");
    }

    @Test
    @DisplayName("全量重建：写完新索引才切别名，切换前后都能搜到")
    void rebuild_switchesAliasAndKeepsServing() throws Exception {
        long novelId = importNovel("重建前后都要在", "重建期间的搜索不该断服。");
        outboxRelay.relayOnce();

        assertThat(awaitHit("重建前后都要在", novelId)).isNotNull();

        String before = currentIndex();
        assertThat(before).isNotNull();

        int indexed = rebuildJob.rebuild();
        assertThat(indexed).isGreaterThanOrEqualTo(1);

        String after = currentIndex();
        assertThat(after).isNotEqualTo(before);
        assertThat(after).isEqualTo("novel_v2");

        assertThat(awaitHit("重建前后都要在", novelId)).isNotNull();
    }

    // ------------------------------------------------------------------ 工具

    private long importNovel(String title, String intro) {
        String content = "山下的江湖与山上的云海都不再安分，剑气从河面一直铺到天边。";
        ParsedNovel parsed = new ParsedNovel(title, "搜索作者", "玄幻", intro,
                List.of(new ParsedChapter(1, "第一章", content, ParsedChapter.countWords(content))));
        return importService.importNovel(parsed, "玄幻");
    }

    /**
     * 找出指定书号的那条命中。
     *
     * <p>不断言 total：搜索是"任意词命中即可"（OR 语义），同一段简介里共用的字
     * 也会让别的书入榜——这是检索的正常行为，不是缺陷。测试只关心"我这本书在不在、排第几"。
     */
    private JsonNode firstHit(JsonNode data, long novelId) {
        for (JsonNode hit : data.path("list")) {
            if (hit.path("novelId").asLong() == novelId) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 等到能搜到为止（最多 5 秒）。
     *
     * <p>ES 是近实时的：文档写完到可搜之间有 1 秒左右的刷新间隔。这不是缺陷，
     * 而是"搜索可以慢半拍"的设计前提。测试等这半拍，而不是把索引的刷新频率调到实时——
     * 后者会让写入吞吐掉一个数量级。
     */
    private JsonNode awaitHit(String keyword, long novelId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode hit = firstHit(search(keyword), novelId);
            if (hit != null) {
                return hit;
            }
            Thread.sleep(100);
        }
        return null;
    }

    private JsonNode awaitHitWithRating(long novelId, double expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = firstHit(search("评分同步之书"), novelId);
            if (last != null && Double.compare(last.path("avgRating").asDouble(), expected) == 0) {
                return last;
            }
            Thread.sleep(100);
        }
        return last;
    }

    private JsonNode awaitSuggest(String prefix) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = readJson(mockMvc.perform(get("/api/v1/search/suggest").param("q", prefix))
                    .andExpect(status().isOk()).andReturn()).path("data");
            if (last.isArray() && last.size() > 0) {
                return last;
            }
            Thread.sleep(100);
        }
        return last == null ? objectMapper.createArrayNode() : last;
    }

    private JsonNode search(String keyword) throws Exception {
        return readJson(mockMvc.perform(get("/api/v1/search").param("q", keyword))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String currentIndex() throws IOException {
        return elasticsearchClient.indices().getAlias(a -> a.name(ALIAS))
                .result().keySet().stream().findFirst().orElse(null);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
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

    /** 假 broker：把消息直接投给消费者，省掉一个 RabbitMQ 容器。 */
    @TestConfiguration
    static class FakeBroker {

        @Bean
        @Primary
        OutboxMessageSender directSender(NovelSearchSyncHandler handler) {
            return message -> {
                MessageProperties headers = new MessageProperties();
                headers.setMessageId(message.messageKey());
                headers.setType(message.eventType());
                headers.setDeliveryTag(1L);
                headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                handler.handle(new Message(message.payloadJson().getBytes(StandardCharsets.UTF_8), headers));
                return true;
            };
        }
    }
}
