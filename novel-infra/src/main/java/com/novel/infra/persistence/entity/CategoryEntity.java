package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code novel_category} 表映射。 */
@Data
@TableName("novel_category")
public class CategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Long parentId;
    private Integer sortNo;
    private String iconKey;
    private LocalDateTime createdAt;
}
