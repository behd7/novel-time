package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code post_novel_ref} 表映射。
 *
 * <p>这张表没有 {@code deleted}：帖子软删除后引用行保留，帖子查询本身会过滤掉，
 * 不需要为引用再维护一份删除状态。
 */
@Data
@TableName("post_novel_ref")
public class PostRefEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long novelId;
    private Integer sortNo;
    private String refText;
    private LocalDateTime createdAt;
}
