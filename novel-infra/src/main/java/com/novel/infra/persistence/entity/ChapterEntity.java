package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code chapter} 表映射（目录项，不含正文）。 */
@Data
@TableName("chapter")
public class ChapterEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;
    private Integer seq;
    private String title;
    private Integer wordCount;
    private Integer isFree;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
