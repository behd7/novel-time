package com.novel.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 榜单配置。
 *
 * <p>权重只在这里出现一次：{@code RedisRankScoreStore} 用它给行为加权，
 * {@code HotScoreCalculator} 用它把库里三个累计列合成同一个尺度。
 * 两处各写一份常量的话，改配置时必然漏掉一处，日榜和总榜就会用两套刻度，
 * 而这种错误在数据上看不出异常——只会让"日榜第一"和"总榜第一"永远不是同一本书。
 */
@Data
@ConfigurationProperties(prefix = "novel.rank")
public class RankProperties {

    /** 关掉后不跑结算任务；导入/重建这类短命进程靠它避免白跑一轮。 */
    private boolean enabled = true;

    /** 结算频率。刷新不会让 {@code novel_rank} 变大——每次都是整份替换。 */
    private String refreshCron = "0 */10 * * * ?";

    /** 每个周期快照写多少行。读接口的 size 上限是 50，200 行足够翻四页。 */
    private int snapshotSize = 200;

    /** 读缓存时长，配 0 表示不缓存（排查"到底是缓存还是库返回的旧数据"时用）。 */
    private Duration cacheTtl = Duration.ofMinutes(5);

    private int viewWeight = 1;
    private int favoriteWeight = 3;
    private int reviewWeight = 2;

    /** 总榜的时间衰减指数；0 表示不衰减。 */
    private double decayExponent = 0.5;

    /** 衰减偏移（天），必须为正：年龄为 0 时它是分母。 */
    private double decayOffsetDays = 2;
}
