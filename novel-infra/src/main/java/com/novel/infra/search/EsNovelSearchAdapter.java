package com.novel.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.novel.common.result.PageResult;
import com.novel.core.search.application.SearchQuery;
import com.novel.core.search.application.SearchSort;
import com.novel.core.search.domain.Highlight;
import com.novel.core.search.domain.NovelDoc;
import com.novel.core.search.domain.SearchHit;
import com.novel.core.search.domain.SuggestItem;
import com.novel.core.search.port.NovelSearchPort;
import com.novel.core.search.port.SearchUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ES 适配器。
 *
 * <p>几个不显眼但很要命的细节：
 * <ul>
 *   <li><b>读写都用别名</b>，不出现 {@code novel_v1} 这种物理名，重建时才可以悄悄换掉。</li>
 *   <li><b>写入带外部版本号</b>：版本更小的写入会被集群拒绝（409），
 *       这正是"迟到的旧消息不许覆盖新数据"的落点，收到 409 要当成正常情况吞掉。</li>
 *   <li><b>读结果用 {@code Map} 而不是映射成类</b>：文档里多一个字段（比如补全用的
 *       {@code titleSuggest}）不该让整个搜索 500，展示层只取自己认识的字段。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsNovelSearchAdapter implements NovelSearchPort {

    private final ElasticsearchClient client;
    private final SearchProperties properties;

    @Override
    public PageResult<SearchHit> search(SearchQuery query) {
        try {
            SearchResponse<Map> response = client.search(buildSearchRequest(query), Map.class);
            List<SearchHit> hits = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) {
                    continue;
                }
                hits.add(new SearchHit(toDoc(source), toHighlight(hit.highlight())));
            }
            var totalHits = response.hits().total();
            long total = totalHits == null ? hits.size() : totalHits.value();
            return PageResult.of(hits, total, query.getPage(), query.getSize());
        } catch (IOException e) {
            throw new SearchUnavailableException("搜索请求失败", e);
        } catch (ElasticsearchException e) {
            throw new SearchUnavailableException("搜索集群返回错误：" + e.getMessage(), e);
        }
    }

    @Override
    public List<SuggestItem> suggest(String prefix, int limit) {
        try {
            SearchRequest request = SearchRequest.of(b -> b
                    .index(properties.getAlias())
                    .size(0)
                    .suggest(sg -> sg.suggesters("title", fs -> fs
                            .prefix(prefix)
                            .completion(c -> c.field(NovelIndexDefinition.F_TITLE_SUGGEST)
                                    .size(limit)
                                    .skipDuplicates(true)))));

            SearchResponse<Map> response = client.search(request, Map.class);
            List<SuggestItem> items = new ArrayList<>();
            List<Suggestion<Map>> suggestions = response.suggest().get("title");
            if (suggestions != null) {
                for (Suggestion<Map> suggestion : suggestions) {
                    if (!suggestion.isCompletion()) {
                        continue;
                    }
                    for (CompletionSuggestOption<Map> option : suggestion.completion().options()) {
                        Map<String, Object> source = option.source();
                        if (source != null) {
                            items.add(new SuggestItem(asLong(source, NovelIndexDefinition.F_NOVEL_ID),
                                    asString(source, NovelIndexDefinition.F_TITLE),
                                    asString(source, NovelIndexDefinition.F_AUTHOR)));
                        }
                    }
                }
            }
            // 中文补全只能从词头匹配：输"云"补不出"青云志"。补不到就让前缀查询兜一层底，
            // 宁可多一次请求，也好过给用户一个空下拉框。
            return items.isEmpty() ? prefixFallback(prefix, limit) : items;
        } catch (IOException e) {
            throw new SearchUnavailableException("搜索建议请求失败", e);
        } catch (ElasticsearchException e) {
            throw new SearchUnavailableException("搜索建议集群返回错误：" + e.getMessage(), e);
        }
    }

    @Override
    public void upsert(NovelDoc doc, long version) {
        upsertInto(properties.getAlias(), doc, version);
    }

    /**
     * 写进指定索引。
     *
     * <p>重建时要写的是**还没挂别名的**新索引，所以不能走 {@link #upsert}（它写别名）。
     * 两条路共用同一段逻辑，保证重建产出的文档和增量同步的一模一样。
     */
    void upsertInto(String index, NovelDoc doc, long version) {
        Map<String, Object> source = toSource(doc);
        IndexRequest<Map<String, Object>> request = IndexRequest.of(r -> r
                .index(index)
                .id(String.valueOf(doc.novelId()))
                .document(source)
                .version(version)
                .versionType(VersionType.External));
        try {
            client.index(request);
        } catch (ElasticsearchException e) {
            if (e.status() == 409) {
                // 版本比集群里的旧，说明这条消息迟到了。丢弃才是正确行为，不是错误。
                log.debug("丢弃过期索引写入 index={} novelId={} version={}", index, doc.novelId(), version);
                return;
            }
            throw new SearchUnavailableException("索引写入失败：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new SearchUnavailableException("索引写入失败", e);
        }
    }

    @Override
    public void delete(long novelId, long version) {
        try {
            client.delete(d -> d.index(properties.getAlias())
                    .id(String.valueOf(novelId))
                    .version(version)
                    .versionType(VersionType.External));
        } catch (ElasticsearchException e) {
            // 404 = 本来就没有；409 = 迟到消息。两者都不该让消费失败重试
            if (e.status() == 404 || e.status() == 409) {
                return;
            }
            throw new SearchUnavailableException("索引删除失败：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new SearchUnavailableException("索引删除失败", e);
        }
    }

    private SearchRequest buildSearchRequest(SearchQuery query) {
        List<Query> filters = new ArrayList<>();
        if (query.categoryId() != null) {
            filters.add(Query.of(q -> q.term(t -> t
                    .field(NovelIndexDefinition.F_CATEGORY_ID).value(query.categoryId()))));
        }
        if (query.status() != null) {
            filters.add(Query.of(q -> q.term(t -> t
                    .field(NovelIndexDefinition.F_STATUS).value(query.status()))));
        }

        // 用 multi_match 而不是 query_string：后者会把用户输入当查询语法解析，
        // 输入 "title:(a OR b)" 就能改写查询语义，等于把查询能力直接开放给外部。
        Query textQuery = Query.of(q -> q.multiMatch(m -> m
                .query(query.keyword())
                .fields(NovelIndexDefinition.F_TITLE + "^3",
                        NovelIndexDefinition.F_AUTHOR + "^2",
                        NovelIndexDefinition.F_INTRO)
                .type(TextQueryType.BestFields)));

        return SearchRequest.of(b -> {
            b.index(properties.getAlias())
                    .from(query.offset())
                    .size(query.getSize())
                    // 分页要精确总数，默认的上限会在超过 10000 条时悄悄变成"约等于"
                    .trackTotalHits(t -> t.enabled(true))
                    .source(s -> s.filter(f -> f.excludes(NovelIndexDefinition.F_TITLE_SUGGEST)))
                    .query(q -> q.bool(bool -> bool.must(textQuery).filter(filters)))
                    .highlight(h -> h
                            .preTags("<em>")
                            .postTags("</em>")
                            .fields(NovelIndexDefinition.F_TITLE, f -> f)
                            .fields(NovelIndexDefinition.F_AUTHOR, f -> f)
                            .fields(NovelIndexDefinition.F_INTRO, f -> f
                                    .numberOfFragments(1)
                                    .fragmentSize(80)
                                    .noMatchSize(80)));
            applySort(b, query.sort());
            return b;
        });
    }

    private void applySort(SearchRequest.Builder builder, SearchSort sort) {
        switch (sort) {
            case RELEVANCE -> builder.sort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
            case LATEST -> builder.sort(s -> s.field(f -> f
                    .field(NovelIndexDefinition.F_LATEST_UPDATE).order(SortOrder.Desc)));
            case HOT -> {
                // 热度分在 M5 之前恒为 0，所以次级排序用阅读数兜住，
                // 等榜单任务接上以后不用改代码，排序自然切换到真实热度
                builder.sort(s -> s.field(f -> f
                        .field(NovelIndexDefinition.F_HOT_SCORE).order(SortOrder.Desc)));
                builder.sort(s -> s.field(f -> f
                        .field(NovelIndexDefinition.F_VIEW_COUNT).order(SortOrder.Desc)));
            }
            case RATING -> {
                builder.sort(s -> s.field(f -> f
                        .field(NovelIndexDefinition.F_AVG_RATING).order(SortOrder.Desc)));
                builder.sort(s -> s.field(f -> f
                        .field(NovelIndexDefinition.F_RATING_COUNT).order(SortOrder.Desc)));
            }
            case WORD_COUNT -> builder.sort(s -> s.field(f -> f
                    .field(NovelIndexDefinition.F_WORD_COUNT).order(SortOrder.Desc)));
        }
        // 同值时按书号兜底排序，否则分页会出现"同一本书在第二页又出现一次"
        builder.sort(s -> s.field(f -> f
                .field(NovelIndexDefinition.F_NOVEL_ID).order(SortOrder.Desc)));
    }

    private List<SuggestItem> prefixFallback(String prefix, int limit) {
        try {
            SearchResponse<Map> response = client.search(b -> b
                    .index(properties.getAlias())
                    .size(limit)
                    .source(s -> s.filter(f -> f.excludes(NovelIndexDefinition.F_TITLE_SUGGEST)))
                    .query(q -> q.matchPhrasePrefix(m -> m
                            .field(NovelIndexDefinition.F_TITLE)
                            .query(prefix))), Map.class);

            List<SuggestItem> items = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    items.add(new SuggestItem(asLong(source, NovelIndexDefinition.F_NOVEL_ID),
                            asString(source, NovelIndexDefinition.F_TITLE),
                            asString(source, NovelIndexDefinition.F_AUTHOR)));
                }
            }
            return items;
        } catch (IOException | ElasticsearchException e) {
            log.warn("补全兜底查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 文档体：{@code _source} 就是这些字段，另外多带一个补全字段。 */
    Map<String, Object> toSource(NovelDoc doc) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(NovelIndexDefinition.F_NOVEL_ID, doc.novelId());
        source.put(NovelIndexDefinition.F_TITLE, doc.title());
        source.put(NovelIndexDefinition.F_AUTHOR, doc.authorName());
        source.put(NovelIndexDefinition.F_COVER, doc.coverKey());
        source.put(NovelIndexDefinition.F_INTRO, doc.intro());
        source.put(NovelIndexDefinition.F_CATEGORY_ID, doc.categoryId());
        source.put(NovelIndexDefinition.F_CATEGORY_NAME, doc.categoryName());
        source.put(NovelIndexDefinition.F_STATUS, doc.status());
        source.put(NovelIndexDefinition.F_WORD_COUNT, doc.wordCount());
        source.put(NovelIndexDefinition.F_CHAPTER_COUNT, doc.chapterCount());
        source.put(NovelIndexDefinition.F_AVG_RATING, doc.avgRating());
        source.put(NovelIndexDefinition.F_RATING_COUNT, doc.ratingCount());
        source.put(NovelIndexDefinition.F_VIEW_COUNT, doc.viewCount());
        source.put(NovelIndexDefinition.F_HOT_SCORE, doc.hotScore());
        source.put(NovelIndexDefinition.F_LATEST_UPDATE, doc.latestUpdateAt());
        source.put(NovelIndexDefinition.F_CREATED_AT, doc.createdAt());
        // completion 字段要的是 {input, weight} 结构，直接塞标题字符串是不生效的
        source.put(NovelIndexDefinition.F_TITLE_SUGGEST, Map.of(
                "input", List.of(doc.title()),
                "weight", (int) Math.min(doc.viewCount(), 100_000L)));
        return source;
    }


    private NovelDoc toDoc(Map<String, Object> source) {
        return new NovelDoc(
                asLong(source, NovelIndexDefinition.F_NOVEL_ID),
                asString(source, NovelIndexDefinition.F_TITLE),
                asString(source, NovelIndexDefinition.F_AUTHOR),
                asString(source, NovelIndexDefinition.F_COVER),
                asLong(source, NovelIndexDefinition.F_CATEGORY_ID),
                asString(source, NovelIndexDefinition.F_CATEGORY_NAME),
                (int) asLong(source, NovelIndexDefinition.F_STATUS),
                asString(source, NovelIndexDefinition.F_INTRO),
                (int) asLong(source, NovelIndexDefinition.F_WORD_COUNT),
                (int) asLong(source, NovelIndexDefinition.F_CHAPTER_COUNT),
                asDouble(source, NovelIndexDefinition.F_AVG_RATING),
                (int) asLong(source, NovelIndexDefinition.F_RATING_COUNT),
                asLong(source, NovelIndexDefinition.F_VIEW_COUNT),
                asDouble(source, NovelIndexDefinition.F_HOT_SCORE),
                asString(source, NovelIndexDefinition.F_LATEST_UPDATE),
                asString(source, NovelIndexDefinition.F_CREATED_AT));
    }

    private Highlight toHighlight(Map<String, List<String>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Highlight.EMPTY;
        }
        return new Highlight(first(raw, NovelIndexDefinition.F_TITLE),
                first(raw, NovelIndexDefinition.F_AUTHOR),
                first(raw, NovelIndexDefinition.F_INTRO));
    }

    private String first(Map<String, List<String>> raw, String field) {
        List<String> fragments = raw.get(field);
        return fragments == null || fragments.isEmpty() ? null : fragments.get(0);
    }

    private String asString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : value.toString();
    }

    private long asLong(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double asDouble(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.doubleValue() : 0d;
    }
}
