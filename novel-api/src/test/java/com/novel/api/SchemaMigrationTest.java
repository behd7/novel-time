package com.novel.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库迁移集成测试。
 *
 * <p>为什么值得单独写：Flyway 脚本一跑错，整个应用都起不来，而这类错误
 * （少个逗号、外键顺序不对、字符集不支持）恰恰是在本地"看起来没问题"的。
 * 用 Testcontainers 起一个干净 MySQL 8，每次从零跑一遍迁移，
 * 就能保证"新同事 clone 下来一定起得来"。
 *
 * <p>注意：这里没有用 @SpringBootTest，因为 M0 阶段只验证 schema，
 * 拉起整个 Spring 上下文会让测试慢 10 倍且依赖 RabbitMQ/ES 是否可用。
 * 单元测试要快，集成测试要真，两者不要混。
 */
@Testcontainers
class SchemaMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("novel")
            .withUsername("novel")
            .withPassword("novel");

    private static final List<String> EXPECTED_TABLES = List.of(
            "user", "user_profile", "user_stat",
            "novel", "novel_category", "novel_tag", "novel_tag_rel",
            "chapter", "chapter_content",
            "bookshelf", "bookshelf_group",
            "novel_rating", "novel_review", "review_reply", "like_record",
            "post", "post_novel_ref", "post_comment",
            "novel_rank", "outbox", "mq_consume_log"
    );

    @Test
    void flyway_should_migrate_clean_database_and_create_all_tables() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);

        List<String> tables = listTables();
        assertThat(tables).containsAll(EXPECTED_TABLES);

        // 分类种子数据要真的落库，否则前端拿不到分类
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM novel_category")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThan(0);
        }
    }

    @Test
    void rating_table_should_enforce_one_vote_per_user() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO novel_rating (novel_id, user_id, score) VALUES (1, 1, 5)");

            // 同一用户对同一本书重复打分，必须被唯一键挡住，而不是悄悄写出两条
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLIntegrityConstraintViolationException.class,
                    () -> statement.executeUpdate(
                            "INSERT INTO novel_rating (novel_id, user_id, score) VALUES (1, 1, 1)"));
        }
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private List<String> listTables() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'novel'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }
}
