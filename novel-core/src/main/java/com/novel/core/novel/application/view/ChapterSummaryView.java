package com.novel.core.novel.application.view;

/** 目录项。不含正文。 */
public record ChapterSummaryView(int seq, String title, int wordCount, boolean free) {
}
