package com.novel.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务。
 *
 * <p>现在有五个：阅读数落库（每分钟）、评分对账（03:30）、索引全量重建（02:00）、
 * Outbox 投递（每秒）、榜单结算（每 10 分钟）。池子大小见 {@code application.yml}，
 * 必须至少等于任务数——否则凌晨重建那几分钟，投递会被排在重建后面，
 * "写完 2 秒内可搜到"这条直接不成立。
 *
 * <p>放在 api 模块而不是 infra，是因为"要不要开定时任务"属于启动装配的决策，
 * infra 只负责提供任务本身。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
