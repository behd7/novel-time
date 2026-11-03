package com.novel.core.user.port;

/**
 * 登录尝试限流。
 *
 * <p>没有这个，登录接口就是免费的密码爆破靶子：8 位纯数字密码，
 * 单机每秒几十次请求，几小时就能撞开一批弱口令账号。
 *
 * <p>按 IP 计入窗口计数，超过阈值直接拒绝，抛 {@code RATE_LIMITED}。
 */
public interface LoginAttemptLimiter {

    /**
     * 记录一次尝试并检查是否超限。
     *
     * @param clientKey 限流维度，当前用客户端 IP
     * @throws com.novel.common.exception.BizException 超限时抛 RATE_LIMITED
     */
    void checkAndRecord(String clientKey);
}
