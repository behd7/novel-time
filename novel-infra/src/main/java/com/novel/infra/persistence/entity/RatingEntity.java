package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code novel_rating} 表映射。一人一票由 {@code uk_novel_user} 保证。 */
@Data
@TableName("novel_rating")
public class RatingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;
    private Long userId;
    private Integer score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
