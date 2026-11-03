package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code bookshelf} 表映射。
 *
 * <p><b>刻意不映射 {@code deleted} 字段</b>：MyBatis-Plus 的逻辑删除是按"实体里有没有这个字段"生效的，
 * 只要实体不含 {@code deleted}，{@code deleteById} 就会真的发 {@code DELETE}。
 * 这是书架的硬性要求 —— 表上有 {@code uk_user_novel(user_id, novel_id)}，
 * 软删除会让"取消收藏 → 再收藏"直接撞唯一键。详见 {@code BookshelfRepository} 的注释。
 */
@Data
@TableName("bookshelf")
public class BookshelfEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long novelId;
    private Long groupId;
    private Integer sortNo;
    private Integer lastChapterSeq;
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
