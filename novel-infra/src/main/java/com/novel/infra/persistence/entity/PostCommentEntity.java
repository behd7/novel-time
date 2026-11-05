package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code post_comment} 表映射。 */
@Data
@TableName("post_comment")
public class PostCommentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private Long rootId;
    private Long parentCommentId;
    private String content;
    private Integer likeCount;
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
