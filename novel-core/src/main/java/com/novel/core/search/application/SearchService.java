package com.novel.core.search.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.PageResult;
import com.novel.common.result.ResultCode;
import com.novel.core.search.SearchTime;
import com.novel.core.search.application.view.SearchHitView;
import com.novel.core.search.application.view.SuggestView;
import com.novel.core.search.domain.NovelDoc;
import com.novel.core.search.domain.SearchHit;
import com.novel.core.search.port.NovelSearchPort;
import com.novel.core.search.port.SearchUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索应用服务。
 *
 * <p>它做的事情很少：校验入参、调用端口、把"引擎挂了"翻译成用户看得懂的业务码。
 * 为什么不做"ES 不可用就回落到 MySQL LIKE"的降级：那条路是全表扫描，
 * 数据量一上来就会把主库拖死，而且用户会以为"这个站的搜索就这么差"。
 * 明确报"搜索服务暂不可用"，至少能让故障被看见——列表、详情、书架这些主流程本来就不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    /** 建议条数上限，下拉框给太多反而没用。 */
    private static final int MAX_SUGGEST = 10;

    private final NovelSearchPort searchPort;

    public PageResult<SearchHitView> search(SearchQuery query) {
        try {
            PageResult<SearchHit> page = searchPort.search(query);
            List<SearchHitView> views = page.getList().stream().map(this::toView).toList();
            return PageResult.of(views, page.getTotal(), page.getPage(), page.getSize());
        } catch (SearchUnavailableException e) {
            log.warn("搜索不可用: {}", e.getMessage());
            throw new BizException(ResultCode.SEARCH_UNAVAILABLE);
        }
    }

    public List<SuggestView> suggest(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("建议关键字不能为空");
        }
        String trimmed = prefix.trim();
        if (trimmed.length() > SearchQuery.MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("建议关键字不能超过 " + SearchQuery.MAX_KEYWORD_LENGTH + " 个字符");
        }
        int safeLimit = Math.min(Math.max(limit, 1), MAX_SUGGEST);
        try {
            return searchPort.suggest(trimmed, safeLimit).stream()
                    .map(item -> new SuggestView(item.novelId(), item.title(), item.authorName()))
                    .toList();
        } catch (SearchUnavailableException e) {
            log.warn("搜索建议不可用: {}", e.getMessage());
            throw new BizException(ResultCode.SEARCH_UNAVAILABLE);
        }
    }

    private SearchHitView toView(SearchHit hit) {
        NovelDoc doc = hit.doc();
        var highlight = hit.highlight();
        return new SearchHitView(
                doc.novelId(), doc.title(), doc.authorName(), doc.coverKey(),
                doc.categoryId(), doc.categoryName(), doc.status(), doc.intro(),
                doc.wordCount(), doc.chapterCount(),
                BigDecimal.valueOf(doc.avgRating()).setScale(2, RoundingMode.HALF_UP),
                doc.ratingCount(), doc.viewCount(), parseTime(doc.latestUpdateAt()),
                new SearchHitView.HighlightView(highlight.title(), highlight.authorName(), highlight.intro()));
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, SearchTime.FORMATTER);
        } catch (RuntimeException e) {
            // 索引里出现脏数据不该让整个搜索 500：这个字段只是展示用的
            log.warn("索引里的时间字段无法解析: {}", text);
            return null;
        }
    }
}
