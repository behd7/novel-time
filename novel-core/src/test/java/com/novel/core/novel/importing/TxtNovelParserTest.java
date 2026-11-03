package com.novel.core.novel.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TXT 解析器单元测试。
 *
 * <p>解析器是纯函数，所以这些用例不需要 Spring、不需要数据库，毫秒级跑完。
 * 重点覆盖三种真实会遇到的脏数据：没有头部、章节号重复、以及整个文件只有正文没有章节标记。
 */
class TxtNovelParserTest {

    private final TxtNovelParser parser = new TxtNovelParser();

    private static final String WITH_HEADER = """
            书名: 青云志
            作者: 佚名
            分类: 玄幻
            简介: 一句话简介
            ---
            第一章 守山人
            青云山终年锁在云里。
            山下的人只说山上住着神仙。

            第二章 刀客
            惊蛰那天，山下上来一个人。
            """;

    @Test
    @DisplayName("标准格式：头部字段与章节都能正确切分")
    void parse_standardFormat() {
        ParsedNovel novel = parser.parse(WITH_HEADER, "fallback");

        assertThat(novel.title()).isEqualTo("青云志");
        assertThat(novel.authorName()).isEqualTo("佚名");
        assertThat(novel.categoryName()).isEqualTo("玄幻");
        assertThat(novel.intro()).isEqualTo("一句话简介");
        assertThat(novel.chapters()).hasSize(2);

        ParsedChapter first = novel.chapters().get(0);
        assertThat(first.seq()).isEqualTo(1);
        assertThat(first.title()).isEqualTo("守山人");
        assertThat(first.content()).contains("青云山终年锁在云里").contains("山下的人只说");
        // 空行不算字数
        assertThat(first.wordCount()).isEqualTo(ParsedChapter.countWords(first.content()));
    }

    @Test
    @DisplayName("章节号按出现顺序重新编号，不信文件里写的数字")
    void parse_renumbersChaptersByOrder() {
        String raw = """
                ---
                第一百零三章 重复的标题
                第一段。

                第一百零三章 又是这个号
                第二段。

                第5章 阿拉伯数字
                第三段。
                """;

        ParsedNovel novel = parser.parse(raw, "无头书");

        assertThat(novel.title()).isEqualTo("无头书");
        assertThat(novel.chapters()).extracting(ParsedChapter::seq).containsExactly(1, 2, 3);
        assertThat(novel.chapters()).extracting(ParsedChapter::title)
                .containsExactly("重复的标题", "又是这个号", "阿拉伯数字");
    }

    @Test
    @DisplayName("没有 --- 分隔符时整篇当正文，标题回退到文件名")
    void parse_withoutHeader() {
        String raw = """
                第一章 起风
                风起了。

                第二章 落雨
                雨落了。
                """;

        ParsedNovel novel = parser.parse(raw, "我的小说");

        assertThat(novel.title()).isEqualTo("我的小说");
        assertThat(novel.authorName()).isEqualTo("佚名");
        assertThat(novel.chapters()).hasSize(2);
    }

    @Test
    @DisplayName("一个章节标记都没有：整篇当一章，而不是报错")
    void parse_noChapterMarker() {
        ParsedNovel novel = parser.parse("这是一段没有章节标记的正文。", "随笔");

        assertThat(novel.chapters()).hasSize(1);
        assertThat(novel.chapters().get(0).seq()).isEqualTo(1);
        assertThat(novel.chapters().get(0).content()).isEqualTo("这是一段没有章节标记的正文。");
    }

    @Test
    @DisplayName("空文件直接拒绝，避免导出一本空书")
    void parse_blankContent_shouldFail() {
        assertThatThrownBy(() -> parser.parse("   \n  \n", "空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("为空");
    }

    @Test
    @DisplayName("字数只数非空白字符")
    void countWords_ignoresWhitespace() {
        assertThat(ParsedChapter.countWords("  你好 世界 \n")).isEqualTo(4);
        assertThat(ParsedChapter.countWords("")).isZero();
        assertThat(ParsedChapter.countWords(null)).isZero();
    }
}
