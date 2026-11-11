package com.novel.api.importing.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批量造数的落库结果。
 *
 * <p>为什么要连真库：这段代码的价值全在三条 SQL 的配合上——书、章、正文分三次写，
 * 章 id 靠回读而不是自己分配。用 mock 只能验证"调用了几次 batchUpdate"，
 * 验证不了"回读的 id 真的是刚才插进去的那几行"，后者恰恰是这里最容易写错的地方。
 *
 * <p>不拉起 Spring 上下文：造数只碰 {@link JdbcTemplate} 和事务管理器，
 * 为了它去连 Redis / RabbitMQ 会让这个测试从 3 秒变成 30 秒。
 */
@Testcontainers
class BulkNovelSeedRunnerTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("novel")
            .withUsername("novel")
            .withPassword("novel");

    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager txManager;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        txManager = new DataSourceTransactionManager(dataSource);
    }

    @BeforeEach
    void cleanBusinessTables() {
        jdbc.update("DELETE FROM chapter_content");
        jdbc.update("DELETE FROM chapter");
        jdbc.update("DELETE FROM novel");
    }

    private BulkNovelSeedRunner runner(int books) {
        return new BulkNovelSeedRunner(jdbc, txManager, books, 2, 3);
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    @Test
    @DisplayName("造出指定本数，每本都有章有正文，聚合列与明细一致")
    void seed_writesBooksChaptersAndContent() {
        int created = runner(5).seed();

        assertThat(created).isEqualTo(5);
        assertThat(count("novel")).isEqualTo(5);
        assertThat(count("chapter")).isBetween(5L * 2, 5L * 3);
        // 每一章都必须有正文：有目录没正文是最难被发现的一种"造数造坏了"
        assertThat(count("chapter_content")).isEqualTo(count("chapter"));

        List<Map<String, Object>> novels = jdbc.queryForList(
                "SELECT id, title, chapter_count, word_count, latest_chapter_id FROM novel ORDER BY id");
        for (Map<String, Object> novel : novels) {
            assertThat((String) novel.get("title")).startsWith(BulkNovelSeedRunner.TITLE_PREFIX);

            long novelId = ((Number) novel.get("id")).longValue();
            Long realChapters = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM chapter WHERE novel_id = ?", Long.class, novelId);
            assertThat(((Number) novel.get("chapter_count")).longValue()).isEqualTo(realChapters);

            // 用 CHAR_LENGTH 而不是 LENGTH：正文是中文，LENGTH 数的是 UTF-8 字节数，
            // 会比项目里统一使用的"字符数"（String.length）大出约三倍
            Long realWords = jdbc.queryForObject(
                    "SELECT SUM(CHAR_LENGTH(content)) FROM chapter_content WHERE novel_id = ?", Long.class, novelId);
            assertThat(((Number) novel.get("word_count")).longValue()).isEqualTo(realWords);

            Long latestChapterId = jdbc.queryForObject(
                    "SELECT id FROM chapter WHERE novel_id = ? ORDER BY seq DESC LIMIT 1", Long.class, novelId);
            assertThat(((Number) novel.get("latest_chapter_id")).longValue()).isEqualTo(latestChapterId);
        }
    }

    @Test
    @DisplayName("重复执行只补差额：第二次跑返回 0，本数不变")
    void seed_isIdempotent() {
        assertThat(runner(3).seed()).isEqualTo(3);
        assertThat(runner(3).seed()).isZero();
        assertThat(count("novel")).isEqualTo(3);

        // 目标调大时应当续跑，而不是从零重来
        assertThat(runner(5).seed()).isEqualTo(2);
        assertThat(count("novel")).isEqualTo(5);
    }
}
