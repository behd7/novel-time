package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** {@code chapter_content} 表映射。主键就是 chapter_id，与目录一一对应。 */
@Data
@TableName("chapter_content")
public class ChapterContentEntity {

    @TableId(value = "chapter_id", type = IdType.INPUT)
    private Long chapterId;

    private Long novelId;
    private String content;
}
