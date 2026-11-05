package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.novel.core.rank.domain.RankEntry;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.infra.persistence.mapper.RankMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 快照仓储的真实 SQL 行为。
 *
 * <p>要验的是"<b>整份替换</b>"这四个字：删掉旧快照、插入新快照、两者在同一个事务里。
 * 用 Mock 只能验"调了 delete 和 insert"，验不出"第二次刷新之后库里只剩这一次的行"——
 * 而那正是这张表最容易出的问题：每十分钟刷一次，一旦不是替换而是追加，
 * 一天之后 {@code novel_rank} 里就会堆出 288 份重复的名次。
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RankSnapshotRepositoryImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("novel_rank_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static RankMapper rankMapper;
    private static RankSnapshotRepositoryImpl repository;

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        dataSource.setMaximumPoolSize(2);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE novel_rank
                    (
                        id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                        period     VARCHAR(8)      NOT NULL,
                        rank_date  DATE            NOT NULL,
                        novel_id   BIGINT UNSIGNED NOT NULL,
                        rank_no    INT UNSIGNED    NOT NULL,
                        score      DECIMAL(12, 4)  NOT NULL DEFAULT 0.0000,
                        created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_period_date_novel (period, rank_date, novel_id),
                        KEY idx_read (period, rank_date, rank_no)
                    ) ENGINE = InnoDB
                      DEFAULT CHARSET = utf8mb4
                    """);
        }

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RankMapper.class);
        factoryBean.setConfiguration(configuration);

        SqlSessionFactory sessionFactory = factoryBean.getObject();
        rankMapper = sessionFactory.openSession(true).getMapper(RankMapper.class);
        repository = new RankSnapshotRepositoryImpl(rankMapper);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void cleanTable() {
        rankMapper.delete(null);
    }

    @Test
    @DisplayName("写入后按名次读回，顺序与分数都不丢")
    void replace_thenRead() {
        repository.replace(RankPeriod.DAILY, TODAY, List.of(
                new RankEntry(11, 1, 9.5), new RankEntry(12, 2, 8.25)));

        assertThat(repository.top(RankPeriod.DAILY, TODAY, 10))
                .extracting(RankEntry::novelId, RankEntry::rankNo)
                .containsExactly(tuple(11L, 1), tuple(12L, 2));
        assertThat(repository.top(RankPeriod.DAILY, TODAY, 10))
                .extracting(RankEntry::score)
                .containsExactly(9.5, 8.25);
    }

    @Test
    @DisplayName("第二次刷新是整份替换，不是追加")
    void replace_isFullReplacement() {
        repository.replace(RankPeriod.DAILY, TODAY, List.of(
                new RankEntry(11, 1, 9.5), new RankEntry(12, 2, 8.25), new RankEntry(13, 3, 7.0)));
        repository.replace(RankPeriod.DAILY, TODAY, List.of(new RankEntry(12, 1, 8.25)));

        assertThat(rankMapper.selectCount(null)).isEqualTo(1L);
        assertThat(repository.top(RankPeriod.DAILY, TODAY, 10))
                .extracting(RankEntry::novelId, RankEntry::rankNo)
                .containsExactly(tuple(12L, 1));
    }

    @Test
    @DisplayName("不同日期、不同周期互不覆盖")
    void replace_isolatedByDateAndPeriod() {
        repository.replace(RankPeriod.DAILY, TODAY, List.of(new RankEntry(1, 1, 5.0)));
        repository.replace(RankPeriod.DAILY, YESTERDAY, List.of(new RankEntry(2, 1, 4.0)));
        repository.replace(RankPeriod.WEEKLY, TODAY, List.of(new RankEntry(3, 1, 3.0)));

        assertThat(repository.top(RankPeriod.DAILY, TODAY, 10))
                .extracting(RankEntry::novelId).containsExactly(1L);
        assertThat(repository.top(RankPeriod.DAILY, YESTERDAY, 10))
                .extracting(RankEntry::novelId).containsExactly(2L);
        assertThat(repository.top(RankPeriod.WEEKLY, TODAY, 10))
                .extracting(RankEntry::novelId).containsExactly(3L);
    }

    @Test
    @DisplayName("空快照把旧榜清干净，不留上一轮的书")
    void replace_empty_clears() {
        repository.replace(RankPeriod.DAILY, TODAY, List.of(new RankEntry(1, 1, 5.0)));

        repository.replace(RankPeriod.DAILY, TODAY, List.of());

        assertThat(repository.top(RankPeriod.DAILY, TODAY, 10)).isEmpty();
    }

    @Test
    @DisplayName("读取按名次升序，limit 生效")
    void top_ordersAndLimits() {
        repository.replace(RankPeriod.DAILY, TODAY, List.of(
                new RankEntry(3, 3, 3.0), new RankEntry(1, 1, 5.0), new RankEntry(2, 2, 4.0)));

        assertThat(repository.top(RankPeriod.DAILY, TODAY, 2))
                .extracting(RankEntry::rankNo)
                .containsExactly(1, 2);
    }
}
