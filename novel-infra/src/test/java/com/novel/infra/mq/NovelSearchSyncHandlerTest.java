package com.novel.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.search.domain.NovelDoc;
import com.novel.core.search.port.NovelSearchPort;
import com.novel.infra.search.NovelDocAssembler;
import com.novel.infra.search.SearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 索引同步的消费逻辑。
 *
 * <p>覆盖三件必须成立的事：
 * <ul>
 *   <li>书不存在（或事件说它删了）时删文档，而不是写一份空文档；</li>
 *   <li>写索引失败会重试，重试耗尽后把异常抛给监听器去 nack；</li>
 *   <li>格式坏掉的消息立刻失败，不进重试循环。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NovelSearchSyncHandlerTest {

    private static final long NOVEL_ID = 7L;
    private static final long VERSION = 3L;

    @Mock
    private NovelRepository novelRepository;
    @Mock
    private NovelSearchPort searchPort;
    @Mock
    private NovelDocAssembler docAssembler;
    @Mock
    private ConsumeLogRepository consumeLogRepository;

    private NovelSearchSyncHandler handler;

    private final Novel novel = new Novel(NOVEL_ID, "青云志", "佚名", null, 1L, 0, "简介",
            900, 5, 10L, LocalDateTime.now(), 0L, 0, BigDecimal.ZERO, 0L, 0, 0,
            BigDecimal.ZERO, VERSION, LocalDateTime.now());

    private final NovelDoc doc = new NovelDoc(NOVEL_ID, "青云志", "佚名", null, 1L, "玄幻",
            0, "简介", 900, 5, 0d, 0, 0L, 0d, null, null);

    @BeforeEach
    void setUp() {
        SearchProperties properties = new SearchProperties();
        // 测试里把退避压成 0，否则每个失败用例要白等 7 秒
        properties.setRetryDelaysMs(new int[]{0, 0, 0});
        handler = new NovelSearchSyncHandler(new ObjectMapper(), novelRepository, searchPort,
                docAssembler, consumeLogRepository, properties);
    }

    @Test
    @DisplayName("书存在：整篇覆盖写，版本号取自消息")
    void existingNovel_upserts() throws Exception {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(docAssembler.assemble(novel)).thenReturn(doc);

        handler.handle(message("novel.search.updated", VERSION));

        verify(searchPort).upsert(doc, VERSION);
        verify(searchPort, never()).delete(anyLong(), anyLong());
        verify(consumeLogRepository).markConsumed("novel.search.updated:7:3", "search-sync");
    }

    @Test
    @DisplayName("书查不到：删文档而不是写空文档")
    void missingNovel_deletes() throws Exception {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.empty());

        handler.handle(message("novel.search.updated", VERSION));

        verify(searchPort).delete(NOVEL_ID, VERSION);
        verify(searchPort, never()).upsert(any(), anyLong());
    }

    @Test
    @DisplayName("删除事件：即使书还在也摘掉文档")
    void deletedEvent_removesDoc() throws Exception {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));

        handler.handle(message("novel.search.deleted", VERSION));

        verify(searchPort).delete(NOVEL_ID, VERSION);
        verify(searchPort, never()).upsert(any(), anyLong());
    }

    @Test
    @DisplayName("写索引失败会重试，前两次失败第三次成功")
    void transientFailure_retries() throws Exception {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(docAssembler.assemble(novel)).thenReturn(doc);
        doThrow(new RuntimeException("es timeout"))
                .doThrow(new RuntimeException("es timeout"))
                .doNothing()
                .when(searchPort).upsert(any(), anyLong());

        handler.handle(message("novel.search.created", VERSION));

        verify(searchPort, times(3)).upsert(doc, VERSION);
    }

    @Test
    @DisplayName("重试耗尽后抛出，交给监听器 nack 进死信")
    void exhausted_throws() {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(docAssembler.assemble(novel)).thenReturn(doc);
        doThrow(new RuntimeException("es down")).when(searchPort).upsert(any(), anyLong());

        assertThatThrownBy(() -> handler.handle(message("novel.search.created", VERSION)))
                .hasMessageContaining("es down");

        // 首次 + 3 次退避重试
        verify(searchPort, times(4)).upsert(any(), anyLong());
        verify(consumeLogRepository, never()).markConsumed(anyString(), anyString());
    }

    @Test
    @DisplayName("缺少 messageId 的消息不重试，直接失败")
    void malformedMessage_failsFast() {
        MessageProperties properties = new MessageProperties();
        properties.setType("novel.search.created");
        Message message = new Message("{\"novelId\":7,\"version\":3}".getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(searchPort, never()).upsert(any(), anyLong());
        verify(novelRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("重复消费：流水唯一键冲突不影响索引写入结果")
    void duplicateDelivery_isHarmless() throws Exception {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(docAssembler.assemble(novel)).thenReturn(doc);
        when(consumeLogRepository.markConsumed(anyString(), anyString())).thenReturn(false);

        handler.handle(message("novel.search.updated", VERSION));

        verify(searchPort).upsert(doc, VERSION);
    }

    private Message message(String eventType, long version) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventType + ":" + NOVEL_ID + ":" + version);
        properties.setType(eventType);
        properties.setDeliveryTag(1L);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        String body = "{\"novelId\":" + NOVEL_ID + ",\"version\":" + version + "}";
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
