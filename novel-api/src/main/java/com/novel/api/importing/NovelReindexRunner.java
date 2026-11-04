package com.novel.api.importing;

import com.novel.infra.search.NovelIndexRebuildJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 手动重建索引入口（仅 {@code reindex} profile 生效）。
 *
 * <p>用法：
 * <pre>
 * java -jar novel-api.jar --spring.profiles.active=reindex
 * </pre>
 *
 * <p>为什么不做一个 {@code POST /search/reindex} 接口：重建会遍历整张表、写几万条文档，
 * 这是个运维动作，不该暴露在业务 API 上，更不该让"有登录态的普通用户"有机会触发。
 * 跟导入一样用 profile + 跑完退出的方式，权限边界最简单——能登服务器的人才能重建。
 */
@Slf4j
@Component
@Profile("reindex")
@RequiredArgsConstructor
public class NovelReindexRunner implements ApplicationRunner {

    private final NovelIndexRebuildJob rebuildJob;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            int indexed = rebuildJob.rebuild();
            log.info("索引重建完成，共写入 {} 条文档", indexed);
        } catch (Exception e) {
            log.error("索引重建失败", e);
            exitCode = 1;
        }
        // 同导入：非 Web 应用不会自动关停，必须显式退出
        System.exit(exitCode);
    }
}
