package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code novel_review} 表映射。 */
@Data
@TableName("novel_review")
public class ReviewEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;
    private Long userId;
    private String content;
    private Integer score;
    private Integer likeCount;
    private Integer replyCount;
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
