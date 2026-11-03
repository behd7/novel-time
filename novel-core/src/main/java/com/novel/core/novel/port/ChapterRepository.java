package com.novel.core.novel.port;

import com.novel.common.result.PageResult;
import com.novel.core.novel.domain.Chapter;
import com.novel.core.novel.importing.ParsedChapter;

import java.util.List;
import java.util.Optional;

/**
 * 章节仓储端口。
 *
 * <p>正文和目录刻意分成两个方法：{@code findBySeq} 返回的是轻量目录项，
 * {@code findContent} 才去读 MEDIUMTEXT。如果图省事把正文塞进 Chapter，
 * 那么"查目录"这一步就会把每章几百 KB 的正文一起拉进内存 —— 目录页只差 20 条记录，
 * 内存却要多扛几 MB。
 */
public interface ChapterRepository {

    Optional<Chapter> findBySeq(long novelId, int seq);

    Optional<Chapter> findById(long chapterId);

    PageResult<Chapter> page(long novelId, int page, int size);

    /** 只读正文；不命中时返回 {@code Optional.empty()}，区别于"章节不存在"。 */
    Optional<String> findContent(long chapterId);

    /** 批量插入目录与正文，返回按入参顺序排列的章节 id。 */
    List<Long> batchInsert(long novelId, List<ParsedChapter> chapters);
}
