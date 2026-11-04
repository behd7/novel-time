package com.novel.infra.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 搜索相关配置。
 *
 * <p>{@code analyzer} 做成可配置不是为了"支持多种引擎"，而是为了能在没有 IK 插件的
 * 机器（以及测试容器）上跑起来：官方 ES 镜像不带 IK，直接用 {@code ik_max_word}
 * 建索引会被拒。默认值仍是 IK，退化必须显式配置，不能悄悄降级。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "novel.search")
public class SearchProperties {

    /** 关掉后不建索引、不消费消息，适合只跑导入的进程。 */
    private boolean enabled = true;

    /** 读写统一走别名，重建时只切别名不动调用方。 */
    private String alias = "novel";

    /** 物理索引前缀：{@code novel_v1}、{@code novel_v2}。 */
    private String indexPrefix = "novel";

    private String analyzer = "ik_max_word";

    private String searchAnalyzer = "ik_smart";

    /** 重建时每批写多少条。 */
    private int bulkSize = 500;

    /** 消费失败的重试间隔（毫秒），长度即重试次数。 */
    private int[] retryDelaysMs = {1000, 2000, 4000};

    /** 索引名的最大保留份数（重建后删旧的）。 */
    private int keepIndexes = 2;
}
