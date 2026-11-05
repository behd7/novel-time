package com.novel.core.support;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;

/**
 * 文本入参的统一校验。
 *
 * <p>空值与长度上限是最常见的一类校验，书评、回复、帖子、评论的规则完全一样；
 * 抽出来是为了让"上限改了但只改一处"成为默认行为。
 */
public final class Texts {

    private Texts() {
    }

    public static String require(String content, int maxLength, String field) {
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, field + "不能为空");
        }
        String text = content.trim();
        if (text.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, field + "不能超过 " + maxLength + " 字");
        }
        return text;
    }
}
