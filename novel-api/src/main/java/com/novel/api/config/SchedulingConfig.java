package com.novel.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务。
 *
 * <p>目前只有一个：{@code ViewCountFlushJob} 每分钟把 Redis 里攒的阅读数刷进数据库。
 * 放在 api 模块而不是 infra，是因为"要不要开定时任务"属于启动装配的决策，
 * infra 只负责提供任务本身。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
