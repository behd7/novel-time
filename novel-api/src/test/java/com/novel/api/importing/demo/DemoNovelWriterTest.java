package com.novel.api.importing.demo;

import com.novel.core.novel.importing.ParsedChapter;
import com.novel.core.novel.importing.ParsedNovel;
import com.novel.core.novel.importing.TxtNovelParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 演示数据生成器的定向测试。
 *
 * <p>不启动 Spring、不连数据库：生成器写文件 + 解析器读回来，都是纯函数，
 * 毫秒级跑完。真正入库的那一步由 {@code DemoNovelImportTest} 用 mock 端口覆盖，
 * 两段一起构成"生成 → 解析 → 导入"的完整链路。
 */
class DemoNovelWriterTest {

    private static final Set<String> TOP_CATEGORIES =
            Set.of("玄幻", "都市", "仙侠", "历史", "科幻", "游戏", "悬疑", "言情");

    private final TxtNovelParser parser = new TxtNovelParser();
    private final DemoNovelWriter writer = new DemoNovelWriter();

    @Test
    @DisplayName("演示库至少 25 本、书名唯一、覆盖 8 个顶级分类")
    void library_coversAllTopCategories() {
        List<DemoNovel> books = DemoNovelLibrary.all();

        assertThat(books).hasSizeGreaterThanOrEqualTo(25);
        assertThat(books).extracting(DemoNovel::title).doesNotHaveDuplicates();
        assertThat(books).extracting(DemoNovel::category)
                .containsAll(TOP_CATEGORIES)
                .allMatch(TOP_CATEGORIES::contains);
        assertThat(books).allSatisfy(book -> {
            assertThat(book.title()).isNotBlank();
            assertThat(book.author()).isNotBlank();
            assertThat(book.intro()).isNotBlank();
            assertThat(book.intro().length()).isBetween(10, 500);
            // 一页 30 章的目录放得下，也不会让单个 TXT 大到不适合做演示
            assertThat(book.chapterCount()).isBetween(8, 12);
        });
    }

    @Test
    @DisplayName("写出的每个文件都能被 TXT 解析器原样读回")
    void write_roundTripsThroughParser(@TempDir Path directory) throws IOException {
        List<DemoNovel> books = DemoNovelLibrary.all();

        List<Path> files = writer.write(directory, books);

        assertThat(files).hasSize(books.size()).allMatch(Files::isRegularFile);
        assertThat(files).extracting(path -> path.getFileName().toString()).doesNotHaveDuplicates();

        for (DemoNovel book : books) {
            Path file = directory.resolve(book.title() + ".txt");
            assertThat(file).as("应生成《%s》的文件", book.title()).exists();

            String raw = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(raw)
                    .startsWith("书名: " + book.title())
                    .contains("\n作者: " + book.author())
                    .contains("\n分类: " + book.category())
                    .contains("\n简介: " + book.intro())
                    .contains("\n---\n");

            ParsedNovel parsed = parser.parse(raw, "fallback");
            assertThat(parsed.title()).isEqualTo(book.title());
            assertThat(parsed.authorName()).isEqualTo(book.author());
            assertThat(parsed.categoryName()).isEqualTo(book.category());
            assertThat(parsed.intro()).isEqualTo(book.intro());
            assertThat(parsed.chapters()).hasSize(book.chapterCount());
            assertThat(parsed.chapters()).extracting(ParsedChapter::seq)
                    .containsExactlyElementsOf(
                            IntStream.rangeClosed(1, book.chapterCount()).boxed().toList());
            assertThat(parsed.chapters()).extracting(ParsedChapter::title).doesNotHaveDuplicates();
            assertThat(parsed.chapters()).allSatisfy(chapter -> {
                assertThat(chapter.content()).isNotBlank();
                assertThat(chapter.wordCount()).isGreaterThan(200);
            });
            assertThat(parsed.totalWords()).isPositive();
        }
    }

    @Test
    @DisplayName("同一份演示库连续生成两次，文件内容逐字节一致")
    void write_isDeterministic(@TempDir Path directory) throws IOException {
        List<DemoNovel> books = DemoNovelLibrary.all();
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");

        writer.write(first, books);
        writer.write(second, books);

        try (Stream<Path> stream = Files.list(first)) {
            List<Path> firstFiles = stream.sorted().toList();
            assertThat(firstFiles).hasSize(books.size());
            for (Path firstFile : firstFiles) {
                Path secondFile = second.resolve(firstFile.getFileName());
                assertThat(secondFile).exists();
                assertThat(Files.mismatch(firstFile, secondFile))
                        .as("%s 两次生成的内容应完全一致", firstFile.getFileName())
                        .isEqualTo(-1L);
            }
        }
    }

    @Test
    @DisplayName("命令行入口按参数写出默认演示库")
    void main_writesLibraryToGivenDirectory(@TempDir Path directory) throws IOException {
        DemoNovelWriter.main(new String[]{directory.toString()});

        try (Stream<Path> stream = Files.list(directory)) {
            assertThat(stream.filter(path -> path.toString().endsWith(".txt")).count())
                    .isEqualTo(DemoNovelLibrary.all().size());
        }
    }
}
