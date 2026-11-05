package com.novel.infra.search;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.novel.core.search.SearchTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 索引结构定义。
 *
 * <p>字段类型全部显式声明，一个 {@code dynamic} 都没留：ES 的自动映射会把
 * {@code "2024-01-01 00:00:00"} 当日期、把 {@code "1"} 当字符串，
 * 一旦某个文档先写进来占了类型，后面真正想要 long 的字段就只能重建索引。
 *
 * <p>分词器的两个参数有讲究：索引时用细粒度（{@code ik_max_word}），查询时用粗粒度
 * （{@code ik_smart}）。反过来的话，"中华人民共和国"会被切出大量无意义片段，
 * 搜出来的东西又碎又不准。
 */
@Component
@RequiredArgsConstructor
public class NovelIndexDefinition {

    public static final String F_TITLE = "title";
    public static final String F_TITLE_SUGGEST = "titleSuggest";
    public static final String F_AUTHOR = "authorName";
    public static final String F_COVER = "coverKey";
    public static final String F_INTRO = "intro";
    public static final String F_CATEGORY_ID = "categoryId";
    public static final String F_CATEGORY_NAME = "categoryName";
    public static final String F_STATUS = "status";
    public static final String F_WORD_COUNT = "wordCount";
    public static final String F_CHAPTER_COUNT = "chapterCount";
    public static final String F_AVG_RATING = "avgRating";
    public static final String F_RATING_COUNT = "ratingCount";
    public static final String F_VIEW_COUNT = "viewCount";
    public static final String F_HOT_SCORE = "hotScore";
    public static final String F_LATEST_UPDATE = "latestUpdateAt";
    public static final String F_CREATED_AT = "createdAt";
    public static final String F_NOVEL_ID = "novelId";

    private final SearchProperties properties;

    public CreateIndexRequest createRequest(String index) {
        String analyzer = properties.getAnalyzer();
        String searchAnalyzer = properties.getSearchAnalyzer();

        return CreateIndexRequest.of(b -> b
                .index(index)
                // 单节点开发环境：1 分片 0 副本。副本数写 1 会让集群一直处于 yellow
                .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                .mappings(m -> m
                        // strict：文档里出现未声明的字段直接写失败，而不是让 ES 猜一个类型建上去。
                        // 猜出来的类型一旦占了位（比如把 "1" 猜成 text），想改只能重建索引。
                        // 代价是给 NovelDoc 加字段时忘了同步映射，写入会报错并进死信——这是故意的。
                        .dynamic(DynamicMapping.Strict)
                        .properties(F_NOVEL_ID, p -> p.long_(l -> l))
                        .properties(F_TITLE, p -> p.text(t -> t.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                        .properties(F_TITLE_SUGGEST, p -> p.completion(c -> c
                                .analyzer(analyzer)
                                .searchAnalyzer(searchAnalyzer)
                                .maxInputLength(50)))
                        .properties(F_AUTHOR, p -> p.text(t -> t.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                        // 封面只存对象键不存完整地址，换 CDN 域名不用重建索引
                        .properties(F_COVER, p -> p.keyword(k -> k))
                        .properties(F_INTRO, p -> p.text(t -> t.analyzer(searchAnalyzer).searchAnalyzer(searchAnalyzer)))
                        .properties(F_CATEGORY_ID, p -> p.long_(l -> l))
                        .properties(F_CATEGORY_NAME, p -> p.keyword(k -> k))
                        .properties(F_STATUS, p -> p.byte_(x -> x))
                        .properties(F_WORD_COUNT, p -> p.integer(i -> i))
                        .properties(F_CHAPTER_COUNT, p -> p.integer(i -> i))
                        .properties(F_AVG_RATING, p -> p.float_(f -> f))
                        .properties(F_RATING_COUNT, p -> p.integer(i -> i))
                        .properties(F_VIEW_COUNT, p -> p.long_(l -> l))
                        .properties(F_HOT_SCORE, p -> p.double_(d -> d))
                        // 格式必须和写入的字符串一字不差，否则建文档时直接 mapper_parsing_exception。
                        // 同时保留 ISO 与毫秒时间戳，方便以后从别的数据源补数据。
                        .properties(F_LATEST_UPDATE, p -> p.date(d -> d
                                .format(SearchTime.PATTERN + "||strict_date_optional_time||epoch_millis")))
                        .properties(F_CREATED_AT, p -> p.date(d -> d
                                .format(SearchTime.PATTERN + "||strict_date_optional_time||epoch_millis")))
                ));
    }
}
