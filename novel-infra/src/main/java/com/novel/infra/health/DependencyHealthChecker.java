package com.novel.infra.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.novel.infra.search.NovelIndexAdmin;
import com.novel.infra.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中间件连通性体检。
 *
 * <p>用途：M0 阶段验证「应用 → 5 个中间件」链路是否真的通。
 * 比看启动日志靠谱得多 —— 启动成功只说明 Bean 装配完了，
 * RabbitMQ/ES/MinIO 都是懒连接，不主动探一下就不知道通没通。
 *
 * <p>注意：这个接口会暴露内网信息（版本号、地址），生产环境应该收到 {@code /actuator} 里并且加权限。
 * 开发期为了方便放在 {@code /api/v1/system/dependencies}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DependencyHealthChecker {

    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConnectionFactory rabbitConnectionFactory;
    private final ElasticsearchClient elasticsearchClient;
    private final S3Client s3Client;
    private final StorageProperties storageProperties;
    /** 搜索索引的状态（别名指向哪个物理索引、有多少文档），M4 起成为常规体检项。 */
    private final NovelIndexAdmin novelIndexAdmin;

    public Map<String, Object> check() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("mysql", probe(() -> {
            try (java.sql.Connection connection = dataSource.getConnection()) {
                DatabaseMetaData meta = connection.getMetaData();
                return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            }
        }));

        result.put("redis", probe(() ->
                stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping())));

        result.put("rabbitmq", probe(() -> {
            try (Connection connection = rabbitConnectionFactory.createConnection()) {
                Object version = connection.getDelegate().getServerProperties().get("version");
                return "AMQP " + version;
            }
        }));

        result.put("elasticsearch", probe(() -> {
            var info = elasticsearchClient.info();
            return "Elasticsearch " + info.version().number();
        }));

        // 集群通不代表搜索能用：索引没建、别名丢了、IK 没装，都会让搜索返回 10601。
        // 把它单列一项，比让运维去猜"ES 明明是绿的"要省事得多。
        result.put("searchIndex", probe(novelIndexAdmin::describe));

        result.put("minio", probe(() -> {
            s3Client.headBucket(b -> b.bucket(storageProperties.getBucket()));
            return "bucket=" + storageProperties.getBucket() + " @ " + storageProperties.getEndpoint();
        }));

        return result;
    }

    private Map<String, Object> probe(Probe probe) {
        long start = System.nanoTime();
        Map<String, Object> item = new LinkedHashMap<>();
        try {
            item.put("ok", true);
            item.put("detail", probe.run());
        } catch (Exception e) {
            log.warn("依赖探测失败", e);
            item.put("ok", false);
            item.put("detail", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        item.put("costMs", (System.nanoTime() - start) / 1_000_000);
        return item;
    }

    @FunctionalInterface
    private interface Probe {
        String run() throws Exception;
    }
}
