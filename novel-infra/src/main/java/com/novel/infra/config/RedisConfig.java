package com.novel.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 装配。
 *
 * <p>key 用 String，value 用 JSON —— 千万不要用 JDK 序列化：
 * 存进去是二进制乱码，`redis-cli` 看不到，线上排查基本等于瞎；而且 JDK 序列化对类名强依赖，
 * 改个包名老数据就读不出来了。
 *
 * <p>{@link GenericJackson2JsonRedisSerializer} 会在 JSON 里额外写一个 {@code @class} 字段，
 * 这样 {@code get()} 回来能还原成原始类型。代价是「跨服务共享缓存」时要保证类路径一致，
 * 所以真正跨服务的数据建议手动指定类型，而不是靠默认类型信息。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
