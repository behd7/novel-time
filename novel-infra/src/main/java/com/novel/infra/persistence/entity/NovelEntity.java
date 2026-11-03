package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@code novel} 表映射。 */
@Data
@TableName("novel")
public class NovelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String authorName;
    private String coverKey;
    private Long categoryId;
    private Integer status;
    private String intro;
    private Integer wordCount;
    private Integer chapterCount;
    private Long latestChapterId;
    private LocalDateTime latestUpdateAt;
    private Integer ratingSum;
    private Integer ratingCount;
    private BigDecimal avgRating;
    private Long viewCount;
    private Integer favoriteCount;
    private Integer reviewCount;
    private BigDecimal hotScore;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
