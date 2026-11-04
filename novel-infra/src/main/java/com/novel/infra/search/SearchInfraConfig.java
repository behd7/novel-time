package com.novel.infra.search;

import com.novel.infra.event.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 搜索与 Outbox 的配置装配。
 *
 * <p>两个配置类都挂在 {@code novel.*} 下，用 {@code @EnableConfigurationProperties} 显式注册，
 * 而不是靠 {@code @Component}：配置对象应该只在装配根被引用一次，
 * 散落在各个组件上会让人搞不清"到底哪些配置项是活的"。
 */
@Configuration
@EnableConfigurationProperties({SearchProperties.class, OutboxProperties.class})
public class SearchInfraConfig {
}
