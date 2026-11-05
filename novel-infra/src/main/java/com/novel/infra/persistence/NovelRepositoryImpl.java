package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.common.result.PageResult;
import com.novel.core.novel.application.NovelQuery;
import com.novel.core.novel.application.NovelSort;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.infra.persistence.entity.CategoryEntity;
import com.novel.infra.persistence.entity.NovelEntity;
import com.novel.infra.persistence.mapper.CategoryMapper;
import com.novel.infra.persistence.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 小说仓储实现。Entity ↔ Domain 的翻译都在这里，core 看不见 MyBatis。 */
@Repository
@RequiredArgsConstructor
public class NovelRepositoryImpl implements NovelRepository {

    private final NovelMapper novelMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public Optional<Novel> findById(long id) {
        return Optional.ofNullable(novelMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Novel> findByTitle(String title) {
        return novelMapper.selectList(Wrappers.<NovelEntity>lambdaQuery()
                        .eq(NovelEntity::getTitle, title)
                        .orderByAsc(NovelEntity::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public List<Novel> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return novelMapper.selectBatchIds(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<Novel> page(NovelQuery query) {
        LambdaQueryWrapper<NovelEntity> wrapper = Wrappers.<NovelEntity>lambdaQuery()
                .eq(query.categoryId() != null, NovelEntity::getCategoryId, query.categoryId())
                .eq(query.status() != null, NovelEntity::getStatus, query.status());

        // 用枚举 + switch 决定排序字段，而不是把前端传的字符串拼进 SQL
        switch (query.sort()) {
            case LATEST -> wrapper.orderByDesc(NovelEntity::getLatestUpdateAt);
            case NEWEST -> wrapper.orderByDesc(NovelEntity::getCreatedAt);
            case WORD_COUNT -> wrapper.orderByDesc(NovelEntity::getWordCount);
            case HOT -> wrapper.orderByDesc(NovelEntity::getHotScore);
        }
        // 加 id 作为次级排序，保证同一毫秒的数据分页时不会重复或漏掉
        wrapper.orderByDesc(NovelEntity::getId);

        Page<NovelEntity> page = novelMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<Novel> novels = page.getRecords().stream().map(this::toDomain).toList();
        return PageResult.of(novels, page.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public List<Category> findAllCategories() {
        List<CategoryEntity> entities = categoryMapper.selectList(
                Wrappers.<CategoryEntity>lambdaQuery()
                        .orderByAsc(CategoryEntity::getParentId)
                        .orderByAsc(CategoryEntity::getSortNo));
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Category> findCategoryByName(String name) {
        CategoryEntity entity = categoryMapper.selectOne(
                Wrappers.<CategoryEntity>lambdaQuery().eq(CategoryEntity::getName, name).last("LIMIT 1"));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<Category> findCategoryById(long id) {
        return Optional.ofNullable(categoryMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Novel> pageAfterId(long afterId, int limit) {
        // limit 只来自配置或常量，仍然夹一道：拼进 SQL 的字符串不接受任何外部输入
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return novelMapper.selectList(Wrappers.<NovelEntity>lambdaQuery()
                        .gt(NovelEntity::getId, afterId)
                        .orderByAsc(NovelEntity::getId)
                        .last("LIMIT " + safeLimit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long create(NewNovel novel) {
        NovelEntity entity = new NovelEntity();
        entity.setTitle(novel.title());
        entity.setAuthorName(novel.authorName());
        entity.setCoverKey(novel.coverKey());
        entity.setCategoryId(novel.categoryId());
        entity.setStatus(novel.status());
        entity.setIntro(novel.intro());
        entity.setWordCount(0);
        entity.setChapterCount(0);
        entity.setViewCount(0L);
        entity.setFavoriteCount(0);
        entity.setReviewCount(0);
        entity.setRatingCount(0);
        novelMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateStats(long novelId, int wordCount, int chapterCount,
                            long latestChapterId, LocalDateTime latestUpdateAt) {
        novelMapper.update(null, Wrappers.<NovelEntity>lambdaUpdate()
                .eq(NovelEntity::getId, novelId)
                .set(NovelEntity::getWordCount, wordCount)
                .set(NovelEntity::getChapterCount, chapterCount)
                .set(NovelEntity::getLatestChapterId, latestChapterId)
                .set(NovelEntity::getLatestUpdateAt, latestUpdateAt)
                // 版本号必须和内容一起变，否则"书变了但版本没变"，索引端会把它当成迟到消息丢掉
                .setSql("version = version + 1"));
    }

    @Override
    public void addViewCount(long novelId, long delta) {
        if (delta != 0) {
            novelMapper.addViewCount(novelId, delta);
        }
    }

    @Override
    public void addFavoriteCount(long novelId, int delta) {
        novelMapper.addFavoriteCount(novelId, delta);
    }

    @Override
    public void addReviewCount(long novelId, int delta) {
        novelMapper.addReviewCount(novelId, delta);
    }

    @Override
    public void addRating(long novelId, long sumDelta, int countDelta) {
        if (sumDelta == 0 && countDelta == 0) {
            return;
        }
        novelMapper.addRating(novelId, sumDelta, countDelta);
    }

    @Override
    public void setRatingStats(long novelId, long sum, int count) {
        novelMapper.setRatingStats(novelId, sum, count);
    }

    @Override
    public void updateHotScores(Map<Long, BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            return;
        }
        // 逐条更新而不是拼一条 CASE WHEN：调用方按批(200 行)传进来，行数有界，
        // 拼超长 SQL 反而要先操心参数上限。书量真涨到十万级再换成批量语句。
        scores.forEach(novelMapper::updateHotScore);
    }

    private Novel toDomain(NovelEntity e) {
        return new Novel(e.getId(), e.getTitle(), e.getAuthorName(), e.getCoverKey(), e.getCategoryId(),
                e.getStatus() == null ? Novel.STATUS_ONGOING : e.getStatus(),
                e.getIntro(),
                e.getWordCount() == null ? 0 : e.getWordCount(),
                e.getChapterCount() == null ? 0 : e.getChapterCount(),
                e.getLatestChapterId(), e.getLatestUpdateAt(),
                e.getRatingSum() == null ? 0L : e.getRatingSum(),
                e.getRatingCount() == null ? 0 : e.getRatingCount(),
                e.getAvgRating(),
                e.getViewCount() == null ? 0L : e.getViewCount(),
                e.getFavoriteCount() == null ? 0 : e.getFavoriteCount(),
                e.getReviewCount() == null ? 0 : e.getReviewCount(),
                e.getHotScore(),
                e.getVersion() == null ? 0L : e.getVersion(),
                e.getCreatedAt());
    }

    private Category toDomain(CategoryEntity e) {
        return new Category(e.getId(), e.getName(), e.getParentId(),
                e.getSortNo() == null ? 0 : e.getSortNo());
    }
}
