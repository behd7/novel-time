package com.novel.api.web;

import com.novel.common.result.Result;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 把 traceId 补进响应体。
 *
 * <p>为什么不用 Filter 直接改 body：那样得缓存并重写整个响应流，代价大且容易踩坑。
 * ResponseBodyAdvice 是在序列化之前拿到的对象，改完直接交给消息转换器，零成本。
 *
 * <p>只处理 {@link Result}，其余（Swagger 的 JSON、静态资源）原样放行。
 */
@RestControllerAdvice
public class ResponseTraceAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class converterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result) {
            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            return traceId == null ? result : result.withTraceId(traceId);
        }
        return body;
    }
}
