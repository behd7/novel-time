package com.novel.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.novel.core.search.domain.NovelDoc;
import com.novel.core.search.port.SearchUnavailableException;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ES 适配器的冲突处理。
 *
 * <p>版本冲突（409）是"迟到的旧消息"的正常结果，必须吞掉。
 * 这里刻意用底层 {@link ResponseException}（它是 {@link IOException} 的子类，
 * 不是高阶客户端的 {@code ElasticsearchException}）来复现真实路径——
 * 之前只判了后者，结果 409 落进 IOException 分支，重试三次后进了死信。
 */
class EsNovelSearchAdapterTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final SearchProperties properties = new SearchProperties();
    private final EsNovelSearchAdapter adapter = new EsNovelSearchAdapter(client, properties);

    @Test
    @DisplayName("写入遇到 409：按过期消息忽略，不抛异常")
    void upsert_versionConflict_isIgnored() throws Exception {
        ResponseException conflict = responseException(409);
        when(client.index(any(IndexRequest.class))).thenThrow(conflict);

        assertThatCode(() -> adapter.upsert(doc(), 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("写入遇到真正的 IO 错误：仍然抛 SearchUnavailableException")
    void upsert_ioFailure_isNotSwallowed() throws Exception {
        when(client.index(any(IndexRequest.class))).thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> adapter.upsert(doc(), 1L))
                .isInstanceOf(SearchUnavailableException.class);
    }

    @Test
    @DisplayName("删除遇到 404/409：按幂等成功处理")
    void delete_notFoundAndConflict_areIgnored() throws Exception {
        ResponseException notFound = responseException(404);
        ResponseException conflict = responseException(409);
        when(client.delete(any(DeleteRequest.class)))
                .thenThrow(notFound)
                .thenThrow(conflict);

        assertThatCode(() -> adapter.delete(1L, 1L)).doesNotThrowAnyException();
        assertThatCode(() -> adapter.delete(1L, 1L)).doesNotThrowAnyException();
    }

    private static ResponseException responseException(int status) throws IOException {
        Response response = mock(Response.class);
        StatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, status, "test");
        RequestLine requestLine = new BasicRequestLine("PUT", "/novel/_doc/1", HttpVersion.HTTP_1_1);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getRequestLine()).thenReturn(requestLine);
        return new ResponseException(response);
    }

    private static NovelDoc doc() {
        return new NovelDoc(1L, "长安旧事", "沈砚", null, 4L, "历史",
                0, "简介", 1000, 10, 0.0, 0, 0, 0.0,
                "2026-11-11 11:00:00", "2026-11-11 11:00:00");
    }
}
