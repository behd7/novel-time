package com.novel.core.novel.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.PageResult;
import com.novel.common.result.ResultCode;
import com.novel.core.novel.application.view.CategoryView;
import com.novel.core.novel.application.view.ChapterSummaryView;
import com.novel.core.novel.application.view.ChapterView;
import com.novel.core.novel.application.view.NovelDetailView;
import com.novel.core.novel.application.view.NovelSummaryView;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.domain.Chapter;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.ChapterRepository;
import com.novel.core.novel.port.NovelCache;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.novel.port.NovelViewCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 阅读侧应用服务：列表、详情、目录、正文、分类。
 *
 * <p>缓存策略就写在这一个类里，没有再做一层"缓存装饰器"——因为需要缓存的只有正文和分类两处，
 * 装饰器反而会让"这个接口到底走没走缓存"变得难以一眼看清。
 * 缓存判断集中在此，读代码时不必跳三个文件才知道数据从哪来。
 */
@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final NovelCache novelCache;
    private final NovelViewCounter viewCounter;

    public PageResult<NovelSummaryView> list(NovelQuery query) {
        PageResult<Novel> page = novelRepository.page(query);
        List<NovelSummaryView> views = page.getList().stream().map(this::toSummary).toList();
        return PageResult.of(views, page.getTotal(), page.getPage(), page.getSize());
    }

    public NovelDetailView detail(long novelId) {
        Novel novel = requireNovel(novelId);

        // 先攒计数再读增量，这样本次访问也算进去
        viewCounter.increment(novelId);
        long viewCount = novel.viewCount() + viewCounter.pendingDelta(novelId);

        Chapter latest = novel.latestChapterId() == null
                ? null
                : chapterRepository.findById(novel.latestChapterId()).orElse(null);

        return new NovelDetailView(
                novel.id(), novel.title(), novel.authorName(), novel.coverKey(), novel.categoryId(),
                novel.status(), novel.intro(), novel.wordCount(), novel.chapterCount(),
                novel.avgRating(), novel.ratingCount(), viewCount, novel.favoriteCount(),
                latest == null ? null : latest.seq(),
                latest == null ? null : latest.title(),
                novel.latestUpdateAt());
    }

    public PageResult<ChapterSummaryView> catalog(long novelId, int page, int size) {
        requireNovel(novelId);
        PageResult<Chapter> chapters = chapterRepository.page(novelId, page, size);
        List<ChapterSummaryView> views = chapters.getList().stream()
                .map(c -> new ChapterSummaryView(c.seq(), c.title(), c.wordCount(), c.free()))
                .toList();
        return PageResult.of(views, chapters.getTotal(), chapters.getPage(), chapters.getSize());
    }

    /**
     * 读某一章正文。
     *
     * <p>顺序是 cache-aside：先查缓存，未命中再查库并回填。缓存里存的是纯文本，
     * 不是序列化后的对象——正文本来就是字符串，没必要为了统一格式再包一层 JSON。
     */
    public ChapterView readChapter(long novelId, int seq) {
        Novel novel = requireNovel(novelId);
        Chapter chapter = chapterRepository.findBySeq(novelId, seq)
                .orElseThrow(() -> new BizException(ResultCode.CHAPTER_NOT_FOUND));

        String content = novelCache.getChapterText(chapter.id());
        if (content == null) {
            content = chapterRepository.findContent(chapter.id())
                    .orElseThrow(() -> new BizException(ResultCode.CHAPTER_NOT_FOUND));
            novelCache.putChapterText(chapter.id(), content);
        }

        Integer prevSeq = seq > 1 ? seq - 1 : null;
        Integer nextSeq = seq < novel.chapterCount() ? seq + 1 : null;

        return new ChapterView(novelId, chapter.id(), chapter.seq(), chapter.title(),
                content, chapter.wordCount(), prevSeq, nextSeq);
    }

    public List<CategoryView> categories() {
        List<Category> cached = novelCache.getCategories();
        if (cached != null) {
            return cached.stream().map(this::toCategoryView).toList();
        }
        List<Category> categories = novelRepository.findAllCategories();
        novelCache.putCategories(categories);
        return categories.stream().map(this::toCategoryView).toList();
    }

    private Novel requireNovel(long novelId) {
        return novelRepository.findById(novelId)
                .orElseThrow(() -> new BizException(ResultCode.NOVEL_NOT_FOUND));
    }

    private NovelSummaryView toSummary(Novel novel) {
        return new NovelSummaryView(
                novel.id(), novel.title(), novel.authorName(), novel.coverKey(), novel.categoryId(),
                novel.status(), novel.intro(), novel.wordCount(), novel.chapterCount(),
                novel.avgRating(), novel.ratingCount(), novel.viewCount(), novel.favoriteCount(),
                novel.latestUpdateAt());
    }

    private CategoryView toCategoryView(Category category) {
        return new CategoryView(category.id(), category.name(), category.parentId(), category.sortNo());
    }
}
