package com.novel.core.novel.importing;

import java.util.List;

/**
 * TXT 解析结果。
 *
 * <p>解析与入库刻意分成两步：解析是纯函数（同样的输入永远同样的输出），
 * 可以单独用单元测试覆盖各种脏格式；入库才碰数据库。混在一起写，
 * 一个"章节标题识别错了"的 bug 就要靠连库才复现得出来。
 */
public record ParsedNovel(
        String title,
        String authorName,
        String categoryName,
        String intro,
        List<ParsedChapter> chapters
) {

    public int totalWords() {
        return chapters.stream().mapToInt(ParsedChapter::wordCount).sum();
    }
}
