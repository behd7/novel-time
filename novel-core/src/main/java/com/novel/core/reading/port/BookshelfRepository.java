package com.novel.core.reading.port;

import com.novel.common.result.PageResult;
import com.novel.core.reading.domain.BookshelfItem;

/**
 * 书架仓储端口。
 *
 * <p>{@code delete} 是**物理删除**，不是软删除。原因在数据库层面：
 * {@code bookshelf} 上有唯一键 {@code uk_user_novel(user_id, novel_id)}，
 * 如果取消收藏只是把 {@code deleted} 置 1，再收藏同一本书就会撞唯一键。
 * 要么把 deleted 放进唯一键（MySQL 做不到条件唯一），要么老老实实物理删除。
 * 书架的收藏关系本来也不值得留审计痕迹，删了就删了。
 */
public interface BookshelfRepository {

    boolean exists(long userId, long novelId);

    void save(long userId, long novelId);

    void delete(long userId, long novelId);

    void updateProgress(long userId, long novelId, int seq);

    PageResult<BookshelfItem> page(long userId, int page, int size);
}
