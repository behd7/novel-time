package com.novel.infra.search;

import com.novel.core.novel.domain.Category;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.search.SearchTime;
import com.novel.core.search.domain.NovelDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 把数据库里的一本书翻译成索引文档。
 *
 * <p>消费、全量重建、重建收尾三处都用它，保证"同一本书无论从哪条路径进索引，内容都一样"——
 * 否则重建后的数据和增量同步的数据长得不一样，排查起来会疯。
 *
 * <p>分类名在这里冗余进来，代价是改分类名要等重建才生效。相比"每次搜索都去 join 分类表"
 * （或者查一次 Redis），这个代价小得多：分类改名是运营一年做不了几次的操作。
 */
@Component
@RequiredArgsConstructor
public class NovelDocAssembler {

    private final NovelRepository novelRepository;

    public NovelDoc assemble(Novel novel) {
        String categoryName = novel.categoryId() == null ? null
                : novelRepository.findCategoryById(novel.categoryId()).map(Category::name).orElse(null);

        return new NovelDoc(
                novel.id(),
                novel.title(),
                novel.authorName(),
                novel.coverKey(),
                novel.categoryId() == null ? 0L : novel.categoryId(),
                categoryName,
                novel.status(),
                novel.intro(),
                novel.wordCount(),
                novel.chapterCount(),
                toDouble(novel.avgRating()),
                novel.ratingCount(),
                novel.viewCount(),
                toDouble(novel.hotScore()),
                format(novel.latestUpdateAt()),
                format(novel.createdAt()));
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(SearchTime.FORMATTER);
    }
}
