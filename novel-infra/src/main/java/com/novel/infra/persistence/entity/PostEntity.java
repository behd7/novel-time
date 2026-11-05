package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@code post} 表映射。 */
@Data
@TableName("post")
public class PostEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String content;
    private String coverKey;
    private Long viewCount;
    private Integer likeCount;
    private Integer commentCount;

    /** M6 不写这一列：最热按读时公式排序，列留给后续压测后再决定是否落盘。 */
    private BigDecimal hotScore;

    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
