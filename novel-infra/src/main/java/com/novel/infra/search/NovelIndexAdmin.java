package com.novel.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 索引运维：建索引、挂别名、切别名、探活。
 *
 * <p>为什么一定要用别名：ES 的字段类型和分词器在索引建好后**不能改**，想换只能重建。
 * 如果调用方直连物理索引 {@code novel_v1}，重建那天就必须停机——先删旧的再建新的，
 * 中间那几分钟搜索全挂。用别名 {@code novel} 指向 {@code novel_v2} 之后，
 * 切换是一次原子的别名更新，读写两端毫无感知。
 *
 * <p>索引名带版本号（{@code novel_v1/v2/…}）而不是时间戳，是为了让人在 {@code _cat/indices}
 * 里一眼看出哪个新哪个旧。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NovelIndexAdmin {

    private final ElasticsearchClient client;
    private final SearchProperties properties;
    private final NovelIndexDefinition definition;

    public boolean aliasExists() throws IOException {
        return client.indices().existsAlias(e -> e.name(properties.getAlias())).value();
    }

    /**
     * 当前别名指向的物理索引；没有就返回 null。
     *
     * <p>用别名查询而不是 {@code _cat/indices} 过滤名字，是因为别名可能指向多个索引
     * （重建切换的一瞬间），拿“第一个”足够表达“现在读的是谁”。
     */
    public String currentIndexName() throws IOException {
        try {
            GetAliasResponse response = client.indices().getAlias(g -> g.name(properties.getAlias()));
            return response.result().keySet().stream().findFirst().orElse(null);
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                return null;
            }
            throw e;
        }
    }

    public boolean indexExists(String index) throws IOException {
        return client.indices().exists(e -> e.index(index)).value();
    }

    /** 幂等初始化：别名在就直接返回，不在就补建索引并挂上。 */
    public void ensure() throws IOException {
        if (aliasExists()) {
            log.info("搜索索引已就绪 alias={} index={}", properties.getAlias(), currentIndexName());
            return;
        }
        // 走到这里说明别名不存在。若此时"同名索引"存在，就是死局：ES 不允许别名与索引重名，
        // 只能人工清理。判断顺序不能反：{@code exists(别名)} 对别名也返回 true，
        // 先判索引会把"别名正常存在"误判成冲突。
        if (indexExists(properties.getAlias())) {
            throw new IllegalStateException(
                    "存在与别名同名的物理索引 [" + properties.getAlias() + "]，Elasticsearch 不允许别名与索引重名。"
                            + "通常是别名缺失时先写入了数据、被自动建成了同名索引。"
                            + "删掉它（DELETE /" + properties.getAlias() + "）后重启即可，MySQL 里没有丢任何东西。");
        }
        String existing = currentIndexName();
        String target = existing != null ? existing : properties.getIndexPrefix() + "_v1";
        if (!indexExists(target)) {
            createIndex(target);
        }
        attachAlias(target);
        log.info("搜索索引初始化完成 alias={} index={}", properties.getAlias(), target);
    }

    /**
     * 下一个可用的物理索引名。
     *
     * <p>不能只按"别名当前指向的版本 +1"算：一次失败的重建可能留下一个没挂别名的孤儿索引，
     * 再算出同一个名字就会撞上 resource_already_exists，重建从此天天失败。
     * 所以这里往后找第一个不存在的名字，孤儿索引自然被跳过。
     */
    public String nextIndexName() throws IOException {
        String current = currentIndexName();
        int version = current == null ? 0 : parseVersion(current);
        String candidate;
        do {
            version++;
            candidate = properties.getIndexPrefix() + "_v" + version;
        } while (indexExists(candidate));
        return candidate;
    }

    public void createIndex(String index) throws IOException {
        client.indices().create(definition.createRequest(index));
        log.info("已创建搜索索引 {}", index);
    }

    public void attachAlias(String index) throws IOException {
        client.indices().updateAliases(u -> u.actions(
                Action.of(a -> a.add(ad -> ad.index(index).alias(properties.getAlias())))));
    }

    /** 原子切换：同一个请求里摘掉旧索引、挂上新索引，不存在“两边都没有”的空窗。 */
    public void switchAlias(String newIndex) throws IOException {
        String old = currentIndexName();
        List<Action> actions = new ArrayList<>();
        if (old != null && !old.equals(newIndex)) {
            actions.add(Action.of(a -> a.remove(r -> r.index(old).alias(properties.getAlias()))));
        }
        actions.add(Action.of(a -> a.add(ad -> ad.index(newIndex).alias(properties.getAlias()))));
        client.indices().updateAliases(u -> u.actions(actions));
        log.info("搜索别名已切换 alias={} {} → {}", properties.getAlias(), old, newIndex);
    }

    /** 刷新让刚写入的文档立刻可搜；重建切别名前必须调用，否则切换后的几百毫秒内搜不到新文档。 */
    public void refresh(String index) throws IOException {
        client.indices().refresh(r -> r.index(index));
    }

    public void deleteIndexQuietly(String index) {
        try {
            client.indices().delete(d -> d.index(index));
            log.info("已删除旧搜索索引 {}", index);
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                log.warn("删除旧索引失败 {}：{}", index, e.getMessage());
            }
        } catch (IOException e) {
            log.warn("删除旧索引失败 {}：{}", index, e.getMessage());
        }
    }

    /**
     * 探一下分词器存不存在。
     *
     * <p>官方 ES 镜像不带 IK，配了 {@code ik_max_word} 却在没装插件的集群上建索引，
     * 报错信息是一长串 JSON，很难一眼看出根因。这里提前用 {@code _analyze} 打一发，
     * 失败时给出一句人话：要么装插件，要么显式把 analyzer 配成 standard。
     */
    public void probeAnalyzer() {
        try {
            client.indices().analyze(a -> a
                    .analyzer(properties.getAnalyzer())
                    .text("诛仙青云志"));
        } catch (ElasticsearchException e) {
            throw new IllegalStateException(
                    "分词器 " + properties.getAnalyzer() + " 不可用（ES 可能没装 IK 插件）。"
                            + "装插件，或把 novel.search.analyzer 配成 standard。原因：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("分词器探测失败", e);
        }
    }

    /** 给依赖体检用的摘要：别名指向哪、有多少文档。 */
    public String describe() {
        try {
            if (!aliasExists()) {
                return "未初始化（alias=" + properties.getAlias() + "）";
            }
            String index = currentIndexName();
            long docs = client.count(c -> c.index(properties.getAlias())).count();
            return "alias=" + properties.getAlias() + " → " + index + "，文档 " + docs + " 条";
        } catch (Exception e) {
            return "不可用：" + e.getMessage();
        }
    }

    private int parseVersion(String indexName) {
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
}
