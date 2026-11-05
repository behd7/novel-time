package com.novel.api.importing;

import com.novel.core.rank.application.RankRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 手动结算榜单（仅 {@code rank} profile 生效）。
 *
 * <p>用法：
 * <pre>
 * java -jar novel-api.jar --spring.profiles.active=rank
 * </pre>
 *
 * <p>为什么不给一个 {@code POST /ranks/refresh}：结算是运维动作，而项目里的 admin 鉴权还没做。
 * 用 profile + 跑完退出的方式，权限边界最简单——能登服务器的人才能触发。
 * 这条和 {@code import} / {@code reindex} 是同一个套路。
 */
@Slf4j
@Component
@Profile("rank")
@RequiredArgsConstructor
public class RankRefreshRunner implements ApplicationRunner {

    private final RankRefreshService rankRefreshService;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            rankRefreshService.refresh(LocalDate.now(clock));
            log.info("榜单结算完成");
        } catch (Exception e) {
            log.error("榜单结算失败", e);
            exitCode = 1;
        }
        // 非 Web 应用不会自动关停，必须显式退出
        System.exit(exitCode);
    }
}
