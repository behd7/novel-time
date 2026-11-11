package com.novel.api.importing.demo;

import com.novel.core.event.NovelChangeType;
import com.novel.core.event.NovelEventPublisher;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.importing.NovelImportService;
import com.novel.core.novel.importing.ParsedChapter;
import com.novel.core.novel.importing.ParsedNovel;
import com.novel.core.novel.importing.TxtNovelParser;
import com.novel.core.novel.port.ChapterRepository;
import com.novel.core.novel.port.NovelCache;
import com.novel.core.novel.port.NovelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生成数据的导入链路测试。
 *
 * <p>不连数据库：把 {@link NovelImportService} 的四个端口（仓储、章节、缓存、事件）换成 mock，
 * 真正跑的是"读文件 → 解析 → 校验分类 → 建书 → 批量插章 → 发事件"这条业务路径。
 * 这样生成器改了格式、或者导入服务的调用顺序变了，这里会先红，而不是等到连库跑 import 才发现。
 */
class DemoNovelImportTest {

    @Test
    @DisplayName("生成的整库能逐一走完导入服务，书名与章节数不丢")
    void generatedLibrary_importsThroughService(@TempDir Path directory) throws IOException {
        List<DemoNovel> books = DemoNovelLibrary.all();
        new DemoNovelWriter().write(directory, books);

        NovelRepository novelRepository = mock(NovelRepository.class);
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        NovelCache cache = mock(NovelCache.class);
        NovelEventPublisher publisher = mock(NovelEventPublisher.class);
        stubFreshImport(novelRepository, chapterRepository);

        NovelImportService service =
                new NovelImportService(novelRepository, chapterRepository, cache, publisher);
        List<Long> novelIds = importAll(directory, service);

        assertThat(novelIds).hasSize(books.size()).doesNotHaveDuplicates();

        ArgumentCaptor<NovelRepository.NewNovel> novelCaptor =
                ArgumentCaptor.forClass(NovelRepository.NewNovel.class);
        verify(novelRepository, times(books.size())).create(novelCaptor.capture());
        assertThat(novelCaptor.getAllValues()).extracting(NovelRepository.NewNovel::title)
                .containsExactlyInAnyOrderElementsOf(books.stream().map(DemoNovel::title).toList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParsedChapter>> chapterCaptor = ArgumentCaptor.forClass(List.class);
        verify(chapterRepository, times(books.size()))
                .batchInsert(anyLong(), chapterCaptor.capture());
        assertThat(chapterCaptor.getAllValues()).allSatisfy(chapters -> {
            assertThat(chapters).hasSizeBetween(8, 12);
            assertThat(chapters).allSatisfy(chapter -> assertThat(chapter.content()).isNotBlank());
        });
        assertThat(chapterCaptor.getAllValues().stream().mapToInt(List::size).sum())
                .isEqualTo(books.stream().mapToInt(DemoNovel::chapterCount).sum());

        verify(cache, times(books.size())).evictCategories();
        verify(publisher, times(books.size()))
                .novelChanged(anyLong(), eq(NovelChangeType.CREATED));
    }

    @Test
    @DisplayName("同一批文件导两遍：第二遍只查重，不再建书也不再插章")
    void importingTwice_skipsExistingTitles(@TempDir Path directory) throws IOException {
        List<DemoNovel> books = DemoNovelLibrary.all();
        new DemoNovelWriter().write(directory, books);

        NovelRepository novelRepository = mock(NovelRepository.class);
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        NovelCache cache = mock(NovelCache.class);
        NovelEventPublisher publisher = mock(NovelEventPublisher.class);

        Map<String, Novel> existing = new HashMap<>();
        when(novelRepository.findByTitle(anyString()))
                .thenAnswer(invocation ->
                        Optional.ofNullable(existing.get(invocation.getArgument(0, String.class))));
        when(novelRepository.findCategoryByName(anyString()))
                .thenAnswer(invocation -> Optional.of(
                        new Category(1L, invocation.getArgument(0, String.class), 0L, 1)));
        when(novelRepository.create(any())).thenAnswer(invocation -> {
            NovelRepository.NewNovel created = invocation.getArgument(0);
            long id = existing.size() + 1L;
            existing.put(created.title(), existingNovel(id, created.title()));
            return id;
        });
        when(chapterRepository.batchInsert(anyLong(), anyList()))
                .thenAnswer(invocation -> {
                    List<ParsedChapter> chapters = invocation.getArgument(1);
                    return chapters.stream().map(chapter -> (long) chapter.seq()).toList();
                });

        NovelImportService service =
                new NovelImportService(novelRepository, chapterRepository, cache, publisher);

        List<Long> firstPass = importAll(directory, service);
        List<Long> secondPass = importAll(directory, service);

        assertThat(secondPass).isEqualTo(firstPass);
        verify(novelRepository, times(books.size())).create(any());
        verify(chapterRepository, times(books.size())).batchInsert(anyLong(), anyList());
        verify(publisher, times(books.size())).novelChanged(anyLong(), any());
    }

    private static void stubFreshImport(NovelRepository novelRepository,
                                        ChapterRepository chapterRepository) {
        when(novelRepository.findByTitle(anyString())).thenReturn(Optional.empty());
        when(novelRepository.findCategoryByName(anyString()))
                .thenAnswer(invocation -> Optional.of(
                        new Category(1L, invocation.getArgument(0, String.class), 0L, 1)));
        AtomicLong ids = new AtomicLong(100);
        when(novelRepository.create(any())).thenAnswer(invocation -> ids.incrementAndGet());
        when(chapterRepository.batchInsert(anyLong(), anyList()))
                .thenAnswer(invocation -> {
                    List<ParsedChapter> chapters = invocation.getArgument(1);
                    return chapters.stream().map(chapter -> (long) chapter.seq()).toList();
                });
    }

    private static List<Long> importAll(Path directory, NovelImportService service) throws IOException {
        TxtNovelParser parser = new TxtNovelParser();
        List<Long> ids = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path file : stream
                    .filter(path -> path.toString().endsWith(".txt"))
                    .sorted()
                    .toList()) {
                ParsedNovel parsed = parser.parse(
                        Files.readString(file, StandardCharsets.UTF_8), "fallback");
                ids.add(service.importNovel(parsed, parsed.categoryName()));
            }
        }
        return ids;
    }

    /** 只用于"已存在"分支，统计字段全部给 0；导入服务只读 id。 */
    private static Novel existingNovel(long id, String title) {
        return new Novel(id, title, "佚名", null, 1L, Novel.STATUS_ONGOING, "简介",
                0, 0, null, null, 0, 0, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, 0, null);
    }
}
