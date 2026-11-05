package com.novel.core.community.domain;

/**
 * 帖子引用的小说卡片。
 *
 * <p>{@code sortNo} 与正文里的 {@code [[ref:N]]} 一一对应，
 * {@code refText} 是保留下来的原始占位符，渲染时按它替换成卡片即可。
 */
public record PostRef(
        Long id,
        Long postId,
        Long novelId,
        int sortNo,
        String refText
) {
}
