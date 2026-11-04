package com.novel.core.search.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 搜索入参的边界。
 *
 * <p>这类校验最容易被当成形式主义，其实每一条都对应一个线上会发生的请求：
 * 前端把空搜索框直接提交、用户粘了一整段正文、列表组件复用了分页状态翻到第 60 页。
 * 前两种是 400（重试也不会变好），最后一种同样要拒绝——放开它等于让任何人
 * 用一次请求让 ES 排序 1200 条再丢掉。
 */
class SearchQueryTest {

    @Test
    @DisplayName("空白关键字直接拒绝")
    void blankKeyword_rejected() {
        assertThatThrownBy(() -> new SearchQuery("   ", null, null, SearchSort.RELEVANCE, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("超长关键字拒绝：防止把整章正文贴进搜索框")
    void tooLongKeyword_rejected() {
        String longText = "剑".repeat(SearchQuery.MAX_KEYWORD_LENGTH + 1);
        assertThatThrownBy(() -> new SearchQuery(longText, null, null, SearchSort.RELEVANCE, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过");
    }

    @Test
    @DisplayName("关键字两端空白被裁掉")
    void keyword_trimmed() {
        SearchQuery query = new SearchQuery("  青云  ", null, null, SearchSort.RELEVANCE, 1, 20);
        assertThat(query.keyword()).isEqualTo("青云");
    }

    @Test
    @DisplayName("size 越界被夹到上限而不是报错")
    void size_clamped() {
        SearchQuery query = new SearchQuery("青云", null, null, SearchSort.RELEVANCE, 1, 500);
        assertThat(query.getSize()).isEqualTo(PageQueryMax.SIZE);
    }

    @Test
    @DisplayName("超出深分页窗口的页码被拒绝")
    void deepPage_rejected() {
        assertThatThrownBy(() -> new SearchQuery("青云", null, null, SearchSort.RELEVANCE, 60, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多翻到");
    }

    @Test
    @DisplayName("排序名非法抛参数异常（走 400，不是 200 + 业务码）")
    void unknownSort_rejected() {
        assertThatThrownBy(() -> SearchSort.from("nonsense"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("排序名两种写法都认：wordCount / word_count")
    void sort_acceptsBothSpellings() {
        assertThat(SearchSort.from("wordCount")).isEqualTo(SearchSort.WORD_COUNT);
        assertThat(SearchSort.from("word_count")).isEqualTo(SearchSort.WORD_COUNT);
        assertThat(SearchSort.from(null)).isEqualTo(SearchSort.RELEVANCE);
    }

    /** 只是为了让断言里的上限来自同一个常量源，避免测试里再写一遍 50。 */
    private static final class PageQueryMax {
        static final int SIZE = com.novel.common.result.PageQuery.MAX_SIZE;
    }
}
