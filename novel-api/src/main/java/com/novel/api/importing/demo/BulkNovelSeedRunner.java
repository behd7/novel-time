package com.novel.api.importing.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 批量造数入口（仅 {@code seed} profile 生效，跑完退出）。
 *
 * <pre>
 * java -jar novel-api.jar --spring.profiles.active=seed --novel.seed.books=5000
 * </pre>
 *
 * <p>为什么需要它：演示库只有 28 本书。28 本书的数据量小到所有查询都能被缓存和缓冲池吸收，
 * 压测出来的数字全是乐观值——"列表接口 P99 3ms"测的是 JVM，不是数据库。要看真实瓶颈，
 * 数据量必须跨过"一页能塞下"的规模。
 *
 * <p>为什么不走 {@code import} 流程逐本导入：那条路径每本书要解析 TXT、校验分类、发一条 MQ 事件，
 * 几千本书会跑上十几分钟，而造数的目的是拿数据、不是验证导入链路（那条链路已有自己的测试）。
 *
 * <p><b>幂等与续跑</b>：以标题前缀 {@value #TITLE_PREFIX} 计数，只补差额，所以中断后重跑会接着造，
 * 不会重复也不会留下半本。每本书一个事务——一本书要么整本在库里，要么完全不在，
 * 这是"续跑"能成立的前提。
 *
 * <p><b>不写 outbox、不建索引</b>：这个 profile 关掉了 {@code novel.search} 与 {@code novel.outbox}
 * （见 application-seed.yml）。造完数要能被搜到，再跑一次 {@code --spring.profiles.active=reindex}。
 */
@Slf4j
@Component
@Profile("seed")
public class BulkNovelSeedRunner implements ApplicationRunner {

    /** 造出来的书统一带这个前缀：既是"已造多少本"的判据，也方便一条 DELETE 清干净。 */
    static final String TITLE_PREFIX = "压测样本-";

    private static final String NOVEL_INSERT = """
            INSERT INTO novel (title, author_name, category_id, status, intro,
                               rating_sum, rating_count, avg_rating,
                               view_count, favorite_count, review_count, hot_score, latest_update_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String NOVEL_UPDATE = """
            UPDATE novel
               SET chapter_count = ?, word_count = ?, latest_chapter_id = ?, latest_update_at = ?
             WHERE id = ?
            """;

    private static final String CHAPTER_INSERT = """
            INSERT INTO chapter (novel_id, seq, title, word_count, is_free) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String CONTENT_INSERT = """
            INSERT INTO chapter_content (chapter_id, novel_id, content) VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int books;
    private final int chaptersMin;
    private final int chaptersMax;

    public BulkNovelSeedRunner(JdbcTemplate jdbcTemplate,
                               PlatformTransactionManager transactionManager,
                               @Value("${novel.seed.books:5000}") int books,
                               @Value("${novel.seed.chapters-min:8}") int chaptersMin,
                               @Value("${novel.seed.chapters-max:40}") int chaptersMax) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.books = books;
        this.chaptersMin = Math.max(1, chaptersMin);
        this.chaptersMax = Math.max(this.chaptersMin, chaptersMax);
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            int created = seed();
            log.info("造数结束：本次新增 {} 本，库中压测样本共 {} 本", created, countExisting());
        } catch (Exception e) {
            log.error("造数失败", e);
            exitCode = 1;
        }
        // 同导入/重建：非 Web 应用不会自动关停，必须显式退出
        System.exit(exitCode);
    }

    /**
     * 补齐到目标本数，返回本次新增的数量。
     *
     * <p>抽成公开方法是为了测试能直接调用——{@link #run} 里带 {@code System.exit}，
     * 在测试里调它会直接杀掉 JVM。
     */
    public int seed() {
        int existing = countExisting();
        if (existing >= books) {
            log.info("已有 {} 本压测样本，达到目标 {} 本，无需造数", existing, books);
            return 0;
        }

        Map<String, Long> categoryIdByName = loadCategories();
        if (categoryIdByName.isEmpty()) {
            throw new IllegalStateException("novel_category 为空：先确认 V2__seed_categories.sql 已执行");
        }
        List<Long> categoryIds = List.copyOf(categoryIdByName.values());
        List<DemoNovel> templates = DemoNovelLibrary.all();

        long startedAt = System.currentTimeMillis();
        int created = 0;
        for (int index = existing; index < books; index++) {
            int bookNo = index + 1;
            DemoNovel template = templates.get(index % templates.size());
            long categoryId = categoryIdByName.getOrDefault(
                    template.category(), categoryIds.get(index % categoryIds.size()));
            insertBook(bookNo, template, categoryId);

            created++;
            if (created % 500 == 0) {
                log.info("已造 {}/{} 本，用时 {}s", existing + created, books,
                        (System.currentTimeMillis() - startedAt) / 1000);
            }
        }
        return created;
    }

    private int countExisting() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM novel WHERE title LIKE ?", Long.class, TITLE_PREFIX + "%");
        return count == null ? 0 : count.intValue();
    }

    /** 分类名 → id。同名只保留第一个，避免树里出现重名时随机落到不同分类。 */
    private Map<String, Long> loadCategories() {
        Map<String, Long> byName = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT id, name FROM novel_category ORDER BY id")) {
            byName.putIfAbsent((String) row.get("name"), ((Number) row.get("id")).longValue());
        }
        return byName;
    }

    /**
     * 一本书一个事务。
     *
     * <p>书、章、正文是三次写，中间任何一次失败都会让这本书变成"有目录没正文"的残本。
     * 而残本会让续跑逻辑误以为这本书已经造好——所以要么整本落库，要么整本回滚。
     */
    private void insertBook(int bookNo, DemoNovel template, long categoryId) {
        // 用 bookNo 做种子：同一本号每轮生成同一份内容，便于复现问题
        Random random = new Random(bookNo * 1_000_003L);
        int chapterCount = chaptersMin + random.nextInt(chaptersMax - chaptersMin + 1);
        String title = TITLE_PREFIX + String.format(Locale.ROOT, "%04d", bookNo) + "-" + template.title();
        LocalDateTime latestUpdateAt = LocalDateTime.now().minusMinutes(random.nextInt(60 * 24));

        List<String> bodies = new ArrayList<>(chapterCount);
        for (int seq = 1; seq <= chapterCount; seq++) {
            bodies.add(DemoProse.body(template.mood(), bookNo, seq));
        }
        long wordCount = bodies.stream().mapToLong(String::length).sum();

        transactionTemplate.executeWithoutResult(status -> {
            long novelId = insertNovel(title, template, categoryId, random, latestUpdateAt);
            Map<Integer, Long> chapterIds = insertChapters(novelId, bookNo, template, bodies);
            insertContents(novelId, chapterIds, bodies);
            long latestChapterId = chapterIds.getOrDefault(chapterCount, 0L);
            jdbcTemplate.update(NOVEL_UPDATE, chapterCount, wordCount, latestChapterId, latestUpdateAt, novelId);
        });
    }

    private long insertNovel(String title, DemoNovel template, long categoryId,
                             Random random, LocalDateTime latestUpdateAt) {
        int ratingCount = random.nextInt(500);
        int ratingSum = ratingCount == 0 ? 0 : ratingCount * (3 + random.nextInt(3));
        BigDecimal avgRating = ratingCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(NOVEL_INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, template.author());
            ps.setLong(3, categoryId);
            ps.setInt(4, random.nextBoolean() ? 1 : 0);
            ps.setString(5, template.intro());
            ps.setInt(6, ratingSum);
            ps.setInt(7, ratingCount);
            ps.setBigDecimal(8, avgRating);
            ps.setLong(9, random.nextInt(200_000));
            ps.setInt(10, random.nextInt(5_000));
            ps.setInt(11, random.nextInt(2_000));
            ps.setBigDecimal(12, BigDecimal.valueOf(random.nextDouble() * 100).setScale(4, RoundingMode.HALF_UP));
            ps.setObject(13, latestUpdateAt);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("新增小说未返回自增 id：" + title);
        }
        return key.longValue();
    }

    /**
     * 批量插章后回读 id。
     *
     * <p>刻意不在应用侧自己分配 id：那需要先 {@code MAX(id)} 再逐条递增，
     * 一旦有别的进程同时插章就会撞主键。多一次 {@code SELECT}（走 {@code idx_novel}）
     * 换掉一个只在本地才安全的假设，是划算的。
     */
    private Map<Integer, Long> insertChapters(long novelId, int bookNo, DemoNovel template, List<String> bodies) {
        List<Object[]> rows = new ArrayList<>(bodies.size());
        for (int seq = 1; seq <= bodies.size(); seq++) {
            String title = "第" + DemoNovelWriter.chineseNumber(seq) + "章 "
                    + DemoProse.title(template.mood(), bookNo, seq);
            rows.add(new Object[]{novelId, seq, title, bodies.get(seq - 1).length(), 1});
        }
        jdbcTemplate.batchUpdate(CHAPTER_INSERT, rows);

        Map<Integer, Long> ids = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT id, seq FROM chapter WHERE novel_id = ?", novelId)) {
            ids.put(((Number) row.get("seq")).intValue(), ((Number) row.get("id")).longValue());
        }
        return ids;
    }

    private void insertContents(long novelId, Map<Integer, Long> chapterIds, List<String> bodies) {
        List<Object[]> rows = new ArrayList<>(bodies.size());
        for (int seq = 1; seq <= bodies.size(); seq++) {
            Long chapterId = chapterIds.get(seq);
            if (chapterId == null) {
                throw new IllegalStateException("章节 seq=" + seq + " 没有回读到 id，novel=" + novelId);
            }
            rows.add(new Object[]{chapterId, novelId, bodies.get(seq - 1)});
        }
        jdbcTemplate.batchUpdate(CONTENT_INSERT, rows);
    }
}
