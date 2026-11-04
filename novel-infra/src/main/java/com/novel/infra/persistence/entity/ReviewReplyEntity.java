package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code review_reply} 表映射。 */
@Data
@TableName("review_reply")
public class ReviewReplyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private Long rootId;
    private Long parentReplyId;
    private Long userId;
    private String content;
    private Integer likeCount;
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
