package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code like_record} 表映射。
 *
 * <p>刻意不映射 {@code deleted}：取消点赞就是删掉这一行，
 * 否则 {@code uk_target_user} 会让"点赞 → 取消 → 再点赞"撞唯一键（与书架同理）。
 */
@Data
@TableName("like_record")
public class LikeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String targetType;
    private Long targetId;
    private LocalDateTime createdAt;
}
