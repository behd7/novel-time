package com.novel.common.exception;

import com.novel.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>只有「可预期的业务失败」才抛它（用户名重复、没权限改别人的评论……），
 * 由 GlobalExceptionHandler 转成 HTTP 200 + 业务错误码。
 * 真正的程序错误（NPE、SQL 异常）不要包装成它，让它们落到 5xxxx，才会被监控告警捕获。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public static BizException of(ResultCode resultCode) {
        return new BizException(resultCode);
    }
}
