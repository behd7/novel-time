package com.novel.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.search.domain.NovelDoc;
import com.novel.infra.persistence.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 索引全量重建。
 *
 * <p>为什么非要有这一步：增量同步只能保证"变更发生了就最终写进去"，保证不了
 * "索引里现有的东西是对的"——消费失败进了死信、手工改过库、索引被误删，
 * 这些都会让索引永久缺数据或留脏数据。全量重建是唯一能把索引拉回真相源的手段。
 *
 * <p>零停机的做法：写到新的物理索引 → 刷新 → 原子切别名 → 删旧的。
 * 切换那一刻前后，读请求要么命中旧索引要么命中新索引，不存在"索引不存在"的空窗。
 *
 * <p>重建期间发生的变更会落在旧索引上（别名还指着它），所以切别名之前要补一刀：
 * 把重建开始后产生过 outbox 的书重新写一遍。这个"收尾补写"把窗口从
 * "整段重建时长"压缩到"最后一次查询到切别名之间的毫秒级"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "novel.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NovelIndexRebuildJob {

    private final ElasticsearchClient client;
    private final NovelIndexAdmin indexAdmin;
    private final NovelRepository novelRepository;
    private final NovelDocAssembler assembler;
    private final EsNovelSearchAdapter adapter;
    private final OutboxMapper outboxMapper;
    private final SearchProperties properties;

    /** 同一个 JVM 里不并发跑两次；跨实例的并发重建靠"各自建各自的 vN" + 后切者胜，可以接受。 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${novel.search.rebuild-cron:0 0 2 * * ?}")
    public void scheduledRebuild() {
        try {
            rebuild();
        } catch (Exception e) {
            // 重建失败不影响线上：旧索引还在，搜索照常
            log.error("搜索索引重建失败，继续使用现有索引", e);
        }
    }

    /**
     * 跑一次重建，返回写入的文档数。
     *
     * <p>公开是为了让"重建"这个动作既能被定时器触发，也能被运维命令按需触发。
     */
    public int rebuild() throws IOException {
        if (!running.compareAndSet(false, true)) {
            log.warn("已有重建任务在执行，本次跳过");
            return 0;
        }
        long watermark = currentWatermark();
        String target = null;
        try {
            target = indexAdmin.nextIndexName();
            indexAdmin.createIndex(target);
            int indexed = bulkIndexAll(target);
            int repaired = repairTail(target, watermark);
            // 不刷新的话，切完别名后最多 1 秒内搜不到新写入的文档
            indexAdmin.refresh(target);
            indexAdmin.switchAlias(target);
            cleanupOldIndexes(target);
            log.info("搜索索引重建完成 index={} 写入={} 补写={}", target, indexed, repaired);
            return indexed + repaired;
        } catch (IOException | RuntimeException e) {
            if (target != null) {
                indexAdmin.deleteIndexQuietly(target);
            }
            throw e;
        } finally {
            running.set(false);
        }
    }

    private int bulkIndexAll(String target) throws IOException {
        int total = 0;
        long lastId = 0;
        while (true) {
            List<Novel> batch = novelRepository.pageAfterId(lastId, properties.getBulkSize());
            if (batch.isEmpty()) {
                break;
            }
            List<BulkOperation> operations = new ArrayList<>(batch.size());
            for (Novel novel : batch) {
                NovelDoc doc = assembler.assemble(novel);
                Map<String, Object> source = adapter.toSource(doc);
                operations.add(BulkOperation.of(op -> op.index(i -> i
                        .index(target)
                        .id(String.valueOf(doc.novelId()))
                        .document(source)
                        // 版本号照搬库里那一行：切别名之后，迟到的旧事件仍然会被 409 挡住
                        .version(novel.version())
                        .versionType(VersionType.External))));
            }
            BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
            if (response.errors()) {
                logBulkErrors(response);
            }
            total += operations.size();
            lastId = batch.get(batch.size() - 1).id();
        }
        return total;
    }

    /** 读水位。读不到就退化成 0，补写范围变大但不会漏。 */
    private long currentWatermark() {
        try {
            return outboxMapper.selectMaxId();
        } catch (RuntimeException e) {
            log.warn("读取 outbox 水位失败，本次重建跳过收尾补写：{}", e.getMessage());
            return Long.MAX_VALUE;
        }
    }

    private int repairTail(String target, long watermark) {
        List<Long> changed;
        try {
            changed = outboxMapper.findAggregateIdsChangedAfter(watermark);
        } catch (RuntimeException e) {
            log.warn("重建收尾补写查询失败，跳过：{}", e.getMessage());
            return 0;
        }
        int repaired = 0;
        for (Long novelId : changed) {
            Optional<Novel> novel = novelRepository.findById(novelId);
            if (novel.isEmpty()) {
                continue;
            }
            try {
                adapter.upsertInto(target, assembler.assemble(novel.get()), novel.get().version());
                repaired++;
            } catch (RuntimeException e) {
                log.warn("重建补写失败 novelId={}：{}", novelId, e.getMessage());
            }
        }
        return repaired;
    }

    private void cleanupOldIndexes(String keepIndex) {
        try {
            GetIndexResponse response = client.indices()
                    .get(g -> g.index(properties.getIndexPrefix() + "_v*"));
            List<String> names = new ArrayList<>(response.result().keySet());
            names.sort(Comparator.comparingInt(this::versionOf));
            int keep = Math.max(properties.getKeepIndexes(), 1);
            for (int i = 0; i < names.size() - keep; i++) {
                String name = names.get(i);
                if (!name.equals(keepIndex)) {
                    indexAdmin.deleteIndexQuietly(name);
                }
            }
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                log.warn("清理旧索引失败：{}", e.getMessage());
            }
        } catch (IOException e) {
            log.warn("清理旧索引失败：{}", e.getMessage());
        }
    }

    private int versionOf(String indexName) {
        int marker = indexName.lastIndexOf("_v");
        if (marker < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(indexName.substring(marker + 2));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void logBulkErrors(BulkResponse response) {
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                log.warn("批量写入部分失败 id={} type={} reason={}",
                        item.id(), item.error().type(), item.error().reason());
            }
        }
    }
}
