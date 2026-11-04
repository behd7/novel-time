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
    /**
     * 搜索索引同步版本号。
     *
     * <p>刻意**不加** {@code @Version}：这个列是给检索引擎丢弃过期消息用的单调序号，
     * 不是乐观锁。加上注解会让 MyBatis-Plus 的乐观锁插件接管它，
     * 两种语义混在一起后，"版本冲突"到底该重试还是该丢弃就说不清了。
     */
    private Long version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
