package com.novel.core.reading.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.PageResult;
import com.novel.common.result.ResultCode;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.ChapterRepository;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.reading.application.view.BookshelfView;
import com.novel.core.reading.domain.BookshelfItem;
import com.novel.core.reading.port.BookshelfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 书架应用服务。
 *
 * <p>三个接口都做了幂等：重复加入书架不报错、取消不存在的收藏不报错、
 * 推送相同进度的进度也不报错。移动端网络抖动导致的重放很常见，
 * 让这些操作天然可重试，比让客户端处理 409 简单得多。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookshelfService {

    private final BookshelfRepository bookshelfRepository;
    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;

    @Transactional
    public void add(long userId, long novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new BizException(ResultCode.NOVEL_NOT_FOUND));
        if (bookshelfRepository.exists(userId, novelId)) {
            return;
        }
        bookshelfRepository.save(userId, novelId);
        novelRepository.addFavoriteCount(novel.id(), 1);
        log.info("加入书架 userId={} novelId={}", userId, novelId);
    }

    @Transactional
    public void remove(long userId, long novelId) {
        if (!bookshelfRepository.exists(userId, novelId)) {
            return;
        }
        bookshelfRepository.delete(userId, novelId);
        novelRepository.addFavoriteCount(novelId, -1);
    }

    public PageResult<BookshelfView> list(long userId, int page, int size) {
        PageResult<BookshelfItem> items = bookshelfRepository.page(userId, page, size);
        if (items.getList().isEmpty()) {
            return PageResult.empty(items.getPage(), items.getSize());
        }

        // 一次批量取小说，避免每本书都单独查一次
        List<Long> novelIds = items.getList().stream().map(BookshelfItem::novelId).toList();
        Map<Long, Novel> novels = novelRepository.findByIds(novelIds).stream()
                .collect(Collectors.toMap(Novel::id, Function.identity()));

        List<BookshelfView> views = items.getList().stream()
                .map(item -> toView(item, novels.get(item.novelId())))
                .filter(java.util.Objects::nonNull)
                .toList();
        return PageResult.of(views, items.getTotal(), items.getPage(), items.getSize());
    }

    /**
     * 更新阅读进度。
     *
     * <p>校验章节真实存在：否则用户随手传个 {@code seq=9999}，书架上就会显示
     * "已读到 9999/20 章"，属于自己给自己挖的坑。
     */
    public void updateProgress(long userId, long novelId, int seq) {
        if (!bookshelfRepository.exists(userId, novelId)) {
            throw new BizException(ResultCode.NOT_FOUND, "这本书不在你的书架里");
        }
        chapterRepository.findBySeq(novelId, seq)
                .orElseThrow(() -> new BizException(ResultCode.CHAPTER_NOT_FOUND));
        bookshelfRepository.updateProgress(userId, novelId, seq);
    }

    private BookshelfView toView(BookshelfItem item, Novel novel) {
        if (novel == null) {
            // 小说被下架但书架记录还在，跳过而不是让整个列表 500
            return null;
        }
        return new BookshelfView(novel.id(), novel.title(), novel.authorName(), novel.coverKey(),
                novel.categoryId(), novel.chapterCount(), item.lastChapterSeq(),
                novel.chapterCount() > item.lastChapterSeq(),
                item.lastReadAt(), item.createdAt());
    }
}
