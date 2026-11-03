package com.novel.core.novel.importing;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.ChapterRepository;
import com.novel.core.novel.port.NovelCache;
import com.novel.core.novel.port.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 小说导入服务。
 *
 * <p>整个导入放在**一个事务**里：小说、目录、正文三张表必须同生共死。
 * 如果中途失败留下一本有目录没正文的"半本书"，阅读页会一直报章节不存在，
 * 排查起来比直接整体回滚麻烦得多。
 *
 * <p>逐条 insert 而不是真批量：这个工具的定位是"运营手工导一次"，
 * 一本 2000 章的书大约 4000 次单行插入，本地 1 秒多能跑完。
 * 真要做批量得换 {@code ExecutorType.BATCH} 的 SqlSession，
 * 那是等数据量上来之后才值得付的复杂度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelImportService {

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final NovelCache novelCache;

    /**
     * @param parsed         解析结果
     * @param categoryName   分类名，为空时落到"玄幻"
     * @return 新建的小说 id
     */
    @Transactional
    public long importNovel(ParsedNovel parsed, String categoryName) {
        if (parsed.chapters().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "《" + parsed.title() + "》没有任何章节");
        }

        // 同一个文件被重复执行是常态（手滑、重跑），按书名跳过而不是再插一本
        Optional<Novel> existing = novelRepository.findByTitle(parsed.title());
        if (existing.isPresent()) {
            log.info("《{}》已存在（novelId={}），跳过", parsed.title(), existing.get().id());
            return existing.get().id();
        }

        Category category = resolveCategory(categoryName);
        long novelId = novelRepository.create(new NovelRepository.NewNovel(
                parsed.title(), parsed.authorName(), null, category.id(),
                Novel.STATUS_ONGOING, parsed.intro()));

        List<Long> chapterIds = chapterRepository.batchInsert(novelId, parsed.chapters());

        long latestChapterId = chapterIds.get(chapterIds.size() - 1);
        novelRepository.updateStats(novelId, parsed.totalWords(), parsed.chapters().size(),
                latestChapterId, LocalDateTime.now());

        // 分类页/列表页的缓存里可能还留着旧数据，导入后清一次
        novelCache.evictCategories();

        log.info("导入完成 novelId={} title={} 章节={} 字数={}",
                novelId, parsed.title(), parsed.chapters().size(), parsed.totalWords());
        return novelId;
    }

    private Category resolveCategory(String categoryName) {
        String name = (categoryName == null || categoryName.isBlank()) ? "玄幻" : categoryName.trim();
        return novelRepository.findCategoryByName(name)
                .orElseThrow(() -> new BizException(ResultCode.BAD_REQUEST, "分类不存在: " + name));
    }
}
