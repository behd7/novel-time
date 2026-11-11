package com.novel.api.importing.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 把演示书库写成 {@link com.novel.core.novel.importing.TxtNovelParser} 认识的 TXT。
 *
 * <p>生成器是纯 JDK 工具，不依赖 Spring 与数据库：先用它产出文件，
 * 再走现有的 {@code --spring.profiles.active=import} 链路入库。
 * 这样"造数据"和"导入数据"各自可以单独验证，也不必为了生成几本书启动整个应用。
 *
 * <p>输出是确定性的：同一份书库连续生成两次，文件逐字节一致。
 * 随机数会让"这次导入的数据和上次不一样"，排查问题时先怀疑数据本身。
 */
public final class DemoNovelWriter {

    private static final String DEFAULT_DIRECTORY = "deploy/samples/demo";

    public List<Path> write(Path directory) throws IOException {
        return write(directory, DemoNovelLibrary.all());
    }

    public List<Path> write(Path directory, List<DemoNovel> books) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(books, "books");
        Files.createDirectories(directory);

        List<Path> written = new ArrayList<>(books.size());
        for (int index = 0; index < books.size(); index++) {
            DemoNovel book = books.get(index);
            Path file = directory.resolve(fileName(book.title()));
            Files.writeString(file, render(book, index), StandardCharsets.UTF_8);
            written.add(file);
        }
        return written;
    }

    /** 按解析器约定的头部 + 章节格式渲染一本书。 */
    String render(DemoNovel book, int bookIndex) {
        StringBuilder text = new StringBuilder(8 * 1024);
        text.append("书名: ").append(book.title()).append('\n')
                .append("作者: ").append(book.author()).append('\n')
                .append("分类: ").append(book.category()).append('\n')
                .append("简介: ").append(book.intro()).append('\n')
                .append("---\n\n");

        for (int seq = 1; seq <= book.chapterCount(); seq++) {
            text.append('第').append(chineseNumber(seq)).append("章 ")
                    .append(DemoProse.title(book.mood(), bookIndex, seq))
                    .append("\n\n")
                    .append(DemoProse.body(book.mood(), bookIndex, seq))
                    .append("\n\n");
        }
        return text.toString();
    }

    /** 文件名去掉 Windows 不允许的字符；书名本身没有这些字符，留着是防御。 */
    private static String fileName(String title) {
        return title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
    }

    /** 章节号用中文数字，和真实小说的排版习惯一致；解析器两种都认。 */
    static String chineseNumber(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value < 10) {
            return digits[value];
        }
        if (value < 20) {
            return value == 10 ? "十" : "十" + digits[value - 10];
        }
        if (value < 100) {
            int tens = value / 10;
            int ones = value % 10;
            return digits[tens] + "十" + (ones == 0 ? "" : digits[ones]);
        }
        return String.valueOf(value);
    }

    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args.length > 0 ? args[0] : DEFAULT_DIRECTORY);
        List<Path> written = new DemoNovelWriter().write(directory);
        System.out.printf("已生成 %d 个演示 TXT → %s%n", written.size(), directory.toAbsolutePath());
    }
}
