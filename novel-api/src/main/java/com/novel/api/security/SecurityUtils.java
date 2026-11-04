package com.novel.api.security;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 取当前登录态的工具。
 *
 * <p>为什么不直接用 {@code @AuthenticationPrincipal}：那个注解在参数上写起来啰嗦，
 * 而且一旦有人忘了加，就会得到一个 null 然后 NPE 变成 500。
 * 集中在这里，取不到就抛 401，行为统一。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static long currentUserId() {
        return currentUser().userId();
    }

    /**
     * 取当前用户 id，未登录返回 {@code null}。
     *
     * <p>用于"登录后能多看到一点、未登录也能看"的接口：书评列表要标出"我点过赞"，
     * 但未登录用户同样应该能浏览评论，不该被 401 拦在门外。
     */
    public static Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            return null;
        }
        return user.userId();
    }
}
