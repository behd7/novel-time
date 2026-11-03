package com.novel.core.novel.application.view;

/**
 * 阅读页视图。
 *
 * @param prevSeq 上一章序号，第一章为 {@code null}
 * @param nextSeq 下一章序号，最后一章为 {@code null}
 */
public record ChapterView(
        long novelId,
        long chapterId,
        int seq,
        String title,
        String content,
        int wordCount,
        Integer prevSeq,
        Integer nextSeq
) {
}
