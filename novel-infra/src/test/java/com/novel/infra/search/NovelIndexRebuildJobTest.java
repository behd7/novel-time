package com.novel.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.search.domain.NovelDoc;
import com.novel.infra.persistence.mapper.OutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 重建的失败语义。
 *
 * <p>要守住的是两条：写入阶段任何失败都不许切别名（否则旧索引被清掉后数据永久缺失）；
 * 切别名失败时要先确认"到底切没切"，别把已经在服务的新索引删了。
 */
@ExtendWith(MockitoExtension.class)
class NovelIndexRebuildJobTest {

    @Mock
    private ElasticsearchClient client;
    @Mock
    private NovelIndexAdmin indexAdmin;
    @Mock
    private NovelRepository novelRepository;
    @Mock
    private NovelDocAssembler assembler;
    @Mock
    private EsNovelSearchAdapter adapter;
    @Mock
    private OutboxMapper outboxMapper;

    private NovelIndexRebuildJob job;

    private final Novel novel = new Novel(1L, "青云志", "佚名", null, 1L, 0, "简介",
            900, 5, 10L, LocalDateTime.now(), 0L, 0, BigDecimal.ZERO, 0L, 0, 0,
            BigDecimal.ZERO, 3L, LocalDateTime.now());

    private final NovelDoc doc = new NovelDoc(1L, "青云志", "佚名", null, 1L, "玄幻", 0, "简介",
            900, 5, 0d, 0, 0L, 0d, null, null);

    @BeforeEach
    void setUp() {
        job = new NovelIndexRebuildJob(client, indexAdmin, novelRepository, assembler,
                adapter, outboxMapper, new SearchProperties());
    }

    @Test
    @DisplayName("批量写入有失败：放弃重建，不切别名，删掉半成品索引")
    void bulkError_abortsWithoutSwitchingAlias() throws Exception {
        stubHappyPath();
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(true));

        assertThatThrownBy(() -> job.rebuild())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("批量写入失败");

        verify(indexAdmin).deleteIndexQuietly("novel_v2");
        verify(indexAdmin, never()).switchAlias(anyString());
    }

    @Test
    @DisplayName("切别名超时但别名已指向新索引：按成功处理，不删索引")
    void switchTimeoutButServing_keepsIndex() throws Exception {
        stubHappyPath();
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(false));
        when(outboxMapper.findAggregateIdsChangedAfter(0L)).thenReturn(List.of());
        when(indexAdmin.currentIndexName()).thenReturn("novel_v2");
        doThrow(new IOException("request timeout")).when(indexAdmin).switchAlias("novel_v2");

        assertThatCode(() -> job.rebuild()).doesNotThrowAnyException();

        verify(indexAdmin, never()).deleteIndexQuietly("novel_v2");
    }

    @Test
    @DisplayName("切别名失败且别名仍指向旧索引：删掉新索引并把异常抛出去")
    void switchFailure_notServing_deletesAndRethrows() throws Exception {
        stubHappyPath();
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(false));
        when(outboxMapper.findAggregateIdsChangedAfter(0L)).thenReturn(List.of());
        when(indexAdmin.currentIndexName()).thenReturn("novel_v1");
        doThrow(new IOException("connection reset")).when(indexAdmin).switchAlias("novel_v2");

        assertThatThrownBy(() -> job.rebuild()).isInstanceOf(IOException.class);

        verify(indexAdmin).deleteIndexQuietly("novel_v2");
    }

    private void stubHappyPath() throws Exception {
        when(outboxMapper.selectMaxId()).thenReturn(0L);
        when(indexAdmin.nextIndexName()).thenReturn("novel_v2");
        when(novelRepository.pageAfterId(0L, 500)).thenReturn(List.of(novel));
        when(assembler.assemble(novel)).thenReturn(doc);
        when(adapter.toSource(doc)).thenReturn(Map.of());
    }

    private BulkResponse response(boolean errors) {
        BulkResponseItem item = BulkResponseItem.of(i -> {
            i.operationType(OperationType.Index)
                    .id("1")
                    .index("novel_v2")
                    .status(errors ? 400 : 201);
            if (errors) {
                i.error(e -> e.type("strict_dynamic_mapping_exception")
                        .reason("mapping set to strict, dynamic introduction of [x] is not allowed"));
            }
            return i;
        });
        return BulkResponse.of(b -> b.took(1).errors(errors).items(item));
    }
}
