package com.novel.infra.search;

import com.novel.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 启动时把索引准备好。
 *
 * <p>和对象存储的初始化一样：**失败只报错不阻断启动**。理由是搜索不是主链路——
 * ES 没起来的时候，列表、详情、正文、书架、评分全都正常，只有搜索返回 10601。
 * 为了一个非核心依赖让整站起不来，是把可用性往下拉而不是往上抬。
 *
 * <p>为什么是 {@link SmartLifecycle} 而不是 {@code ApplicationRunner}：监听器容器在
 * 上下文刷新时就启动了，比 ApplicationRunner 更早。而"别名叫 {@code novel}、索引还不存在"
 * 这个组合是危险的——消费端此时写别名，Elasticsearch 会把 {@code novel} **自动建成一个同名索引**，
 * 之后别名永远建不起来（ES 不允许别名与索引重名），搜索只能一直返回 10601。
 * 把初始化提到 {@code Integer.MIN_VALUE} 阶段，就能保证它先于任何消费动作完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "novel.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NovelIndexBootstrap implements SmartLifecycle {

    private final NovelIndexAdmin indexAdmin;
    private volatile boolean running;

    @Override
    public void start() {
        try {
            indexAdmin.probeAnalyzer();
            indexAdmin.ensure();
        } catch (Exception e) {
            log.error("搜索索引初始化失败，搜索将返回 {}（{}）。原因：{}",
                    ResultCode.SEARCH_UNAVAILABLE.getCode(),
                    ResultCode.SEARCH_UNAVAILABLE.getMessage(),
                    e.getMessage());
            log.debug("搜索索引初始化失败详情", e);
        } finally {
            running = true;
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 最早启动：必须早于 Rabbit 的监听器容器，否则写别名的瞬间会把它自动建成同名索引。 */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }
}
