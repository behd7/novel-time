package com.novel.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>约定：{@code code == 0} 表示成功，其余为业务/系统错误码，具体见 {@link ResultCode}。
 *
 * <p>对象是不可变的；traceId 由 {@code ResponseTraceAdvice} 在出站时通过 {@link #withTraceId} 拷贝补上，
 * 这样既能打印链路 id，又不用为了填一个字段把对象改成可变。
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Result<T> implements Serializable {

    /** 成功码。 */
    public static final int SUCCESS_CODE = 0;

    private final int code;
    private final String message;
    private final T data;
    private final String traceId;

    public Result(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS_CODE, "ok", null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "ok", data, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    /** 返回一个带 traceId 的副本，用于响应出站。 */
    public Result<T> withTraceId(String traceId) {
        return new Result<>(this.code, this.message, this.data, traceId);
    }

    public boolean isSuccess() {
        return this.code == SUCCESS_CODE;
    }

    @JsonProperty("code")
    public int getCode() {
        return code;
    }
}
