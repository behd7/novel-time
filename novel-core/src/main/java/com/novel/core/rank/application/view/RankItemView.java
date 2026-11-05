package com.novel.core.rank.application.view;

import com.novel.core.novel.application.view.NovelSummaryView;

/**
 * 榜单里的一项。
 *
 * <p>小说卡片直接复用列表页的 {@link NovelSummaryView}，不复制字段。
 * 榜单页和列表页展示的东西本来就应该一致，各写一份的结果是"榜单里能看到的字段，
 * 点进详情页发现没有"，这种不一致没人会主动去修。
 *
 * @param rankNo 名次，从 1 开始
 * @param score  热度分，只在同一周期内有比较意义（日榜和总榜的尺度不同）
 */
public record RankItemView(int rankNo, double score, NovelSummaryView novel) {
}
