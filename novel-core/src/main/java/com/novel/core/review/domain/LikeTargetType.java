package com.novel.core.review.domain;

/**
 * 点赞对象类型。
 *
 * <p>一张 {@code like_record} 表服务所有可点赞的实体，靠 {@code (target_type, target_id, user_id)}
 * 唯一键保证"同一人对同一对象只能点一次"。用字符串而不是数字存类型，
 * 是为了在数据库里直接看得懂（{@code POST} 比 {@code 3} 好排查）。
 */
public enum LikeTargetType {

    REVIEW,
    REPLY,
    POST,
    POST_COMMENT;

    public static LikeTargetType from(String value) {
        for (LikeTargetType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的点赞对象: " + value);
    }
}
