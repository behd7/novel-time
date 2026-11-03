package com.novel.core.novel.importing;

/**
 * 解析出的一章。
 *
 * @param seq     章节序号，从 1 开始连续
 * @param title   章节标题，不含"第N章"前缀
 * @param content 正文
 * @param wordCount 正文字数（不含空白），导入时写死，避免每次列表页去数字符
 */
public record ParsedChapter(int seq, String title, String content, int wordCount) {

    /** 按非空白字符计数：中文没有"词"的边界，按字符算是小说站通行的做法。 */
    public static int countWords(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            if (!Character.isWhitespace(content.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
