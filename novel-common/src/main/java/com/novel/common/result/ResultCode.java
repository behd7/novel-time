package com.novel.common.result;

import lombok.Getter;

/**
 * 错误码字典。
 *
 * <p>分段规则：
 * <ul>
 *   <li>0：成功</li>
 *   <li>4xxxx：客户端错误（参数、鉴权、限流），与 HTTP 状态码对齐，便于前端统一处理</li>
 *   <li>5xxxx：服务端错误</li>
 *   <li>1xxxx：业务错误，按域划分（1-1 用户 1-2 小说 1-3 阅读 1-4 评分评论 1-5 社区 1-6 搜索）</li>
 * </ul>
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),

    // ---------- 客户端 ----------
    BAD_REQUEST(40000, "请求参数不合法"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "没有操作权限"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    RATE_LIMITED(42900, "操作太频繁，请稍后再试"),

    // ---------- 服务端 ----------
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试"),

    // ---------- 业务：用户 ----------
    USERNAME_EXISTS(10101, "用户名已被占用"),
    USER_NOT_FOUND(10102, "用户不存在"),
    PASSWORD_INCORRECT(10103, "用户名或密码错误"),
    ACCOUNT_DISABLED(10104, "账号已被封禁"),
    TOKEN_INVALID(10105, "登录凭证无效，请重新登录"),

    // ---------- 业务：小说 ----------
    NOVEL_NOT_FOUND(10201, "小说不存在"),
    CHAPTER_NOT_FOUND(10202, "章节不存在"),

    // ---------- 业务：评分评论 ----------
    ALREADY_RATED(10401, "你已经评过分了，修改评分请使用更新接口"),
    REVIEW_NOT_FOUND(10402, "评论不存在"),
    REVIEW_NO_PERMISSION(10403, "只能操作自己发布的内容"),

    // ---------- 业务：社区 ----------
    POST_NOT_FOUND(10501, "帖子不存在"),
    POST_NO_PERMISSION(10502, "只能操作自己发布的内容"),
    POST_COMMENT_NOT_FOUND(10503, "帖子评论不存在"),

    // ---------- 业务：搜索 ----------
    SEARCH_UNAVAILABLE(10601, "搜索服务暂不可用"),
    ;

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
