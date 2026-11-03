package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.common.result.PageResult;
import com.novel.core.novel.domain.Chapter;
import com.novel.core.novel.importing.ParsedChapter;
import com.novel.core.novel.port.ChapterRepository;
import com.novel.infra.persistence.entity.ChapterContentEntity;
import com.novel.infra.persistence.entity.ChapterEntity;
import com.novel.infra.persistence.mapper.ChapterContentMapper;
import com.novel.infra.persistence.mapper.ChapterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 章节仓储实现。目录与正文分表读写，见 {@link ChapterRepository} 的注释。 */
@Repository
@RequiredArgsConstructor
public class ChapterRepositoryImpl implements ChapterRepository {

    private final ChapterMapper chapterMapper;
    private final ChapterContentMapper chapterContentMapper;

    @Override
    public Optional<Chapter> findBySeq(long novelId, int seq) {
        ChapterEntity entity = chapterMapper.selectOne(Wrappers.<ChapterEntity>lambdaQuery()
                .eq(ChapterEntity::getNovelId, novelId)
                .eq(ChapterEntity::getSeq, seq));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<Chapter> findById(long chapterId) {
        return Optional.ofNullable(chapterMapper.selectById(chapterId)).map(this::toDomain);
    }

    @Override
    public PageResult<Chapter> page(long novelId, int pageNum, int size) {
        Page<ChapterEntity> page = chapterMapper.selectPage(new Page<>(pageNum, size),
                Wrappers.<ChapterEntity>lambdaQuery()
                        .eq(ChapterEntity::getNovelId, novelId)
                        .orderByAsc(ChapterEntity::getSeq));
        List<Chapter> chapters = page.getRecords().stream().map(this::toDomain).toList();
        return PageResult.of(chapters, page.getTotal(), pageNum, size);
    }

    @Override
    public Optional<String> findContent(long chapterId) {
        ChapterContentEntity entity = chapterContentMapper.selectById(chapterId);
        return entity == null ? Optional.empty() : Optional.ofNullable(entity.getContent());
    }

    @Override
    public List<Long> batchInsert(long novelId, List<ParsedChapter> chapters) {
        List<Long> ids = new ArrayList<>(chapters.size());
        for (ParsedChapter chapter : chapters) {
            ChapterEntity entity = new ChapterEntity();
            entity.setNovelId(novelId);
            entity.setSeq(chapter.seq());
            entity.setTitle(chapter.title());
            entity.setWordCount(chapter.wordCount());
            entity.setIsFree(1);
            chapterMapper.insert(entity);

            ChapterContentEntity content = new ChapterContentEntity();
            content.setChapterId(entity.getId());
            content.setNovelId(novelId);
            content.setContent(chapter.content());
            chapterContentMapper.insert(content);

            ids.add(entity.getId());
        }
        return ids;
    }

    private Chapter toDomain(ChapterEntity e) {
        return new Chapter(e.getId(), e.getNovelId(), e.getSeq(), e.getTitle(),
                e.getWordCount() == null ? 0 : e.getWordCount(),
                e.getIsFree() == null || e.getIsFree() == 1,
                e.getCreatedAt());
    }
}
