package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.common.result.PageResult;
import com.novel.core.reading.domain.BookshelfItem;
import com.novel.core.reading.port.BookshelfRepository;
import com.novel.infra.persistence.entity.BookshelfEntity;
import com.novel.infra.persistence.mapper.BookshelfMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** 书架仓储实现。删除是物理删除，原因见 {@link BookshelfRepository}。 */
@Repository
@RequiredArgsConstructor
public class BookshelfRepositoryImpl implements BookshelfRepository {

    private final BookshelfMapper bookshelfMapper;

    @Override
    public boolean exists(long userId, long novelId) {
        return bookshelfMapper.exists(Wrappers.<BookshelfEntity>lambdaQuery()
                .eq(BookshelfEntity::getUserId, userId)
                .eq(BookshelfEntity::getNovelId, novelId));
    }

    @Override
    public void save(long userId, long novelId) {
        BookshelfEntity entity = new BookshelfEntity();
        entity.setUserId(userId);
        entity.setNovelId(novelId);
        entity.setSortNo(0);
        entity.setLastChapterSeq(0);
        bookshelfMapper.insert(entity);
    }

    @Override
    public void delete(long userId, long novelId) {
        bookshelfMapper.delete(Wrappers.<BookshelfEntity>lambdaQuery()
                .eq(BookshelfEntity::getUserId, userId)
                .eq(BookshelfEntity::getNovelId, novelId));
    }

    @Override
    public void updateProgress(long userId, long novelId, int seq) {
        bookshelfMapper.update(null, Wrappers.<BookshelfEntity>lambdaUpdate()
                .eq(BookshelfEntity::getUserId, userId)
                .eq(BookshelfEntity::getNovelId, novelId)
                .set(BookshelfEntity::getLastChapterSeq, seq)
                .set(BookshelfEntity::getLastReadAt, LocalDateTime.now()));
    }

    @Override
    public PageResult<BookshelfItem> page(long userId, int pageNum, int size) {
        Page<BookshelfEntity> page = bookshelfMapper.selectPage(new Page<>(pageNum, size),
                Wrappers.<BookshelfEntity>lambdaQuery()
                        .eq(BookshelfEntity::getUserId, userId)
                        // 最近读过的排前面；从没读过（last_read_at 为 null）的退到后面
                        .orderByDesc(BookshelfEntity::getLastReadAt)
                        .orderByDesc(BookshelfEntity::getId));
        List<BookshelfItem> items = page.getRecords().stream()
                .map(e -> new BookshelfItem(e.getUserId(), e.getNovelId(), e.getGroupId(),
                        e.getLastChapterSeq() == null ? 0 : e.getLastChapterSeq(),
                        e.getLastReadAt(), e.getCreatedAt()))
                .toList();
        return PageResult.of(items, page.getTotal(), pageNum, size);
    }
}
