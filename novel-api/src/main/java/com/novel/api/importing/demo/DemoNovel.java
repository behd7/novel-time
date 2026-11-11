package com.novel.api.importing.demo;

/**
 * 一本演示书的元数据。
 *
 * @param mood 题材，决定用哪一组段落与章节标题；不写进 TXT，只用于生成
 */
public record DemoNovel(
        String title,
        String author,
        String category,
        String intro,
        int chapterCount,
        DemoMood mood
) {

    public enum DemoMood {
        HISTORY,
        SUSPENSE,
        XIANXIA,
        ROMANCE,
        SCIFI,
        URBAN
    }
}
