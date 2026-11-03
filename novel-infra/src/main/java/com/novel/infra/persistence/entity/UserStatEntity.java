package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code user_stat} 表映射。 */
@Data
@TableName("user_stat")
public class UserStatEntity {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Integer bookshelfCount;

    private Integer reviewCount;

    private Integer postCount;

    private Integer likeReceived;

    private LocalDateTime updatedAt;
}
