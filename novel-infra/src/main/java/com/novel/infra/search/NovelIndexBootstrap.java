package com.novel.infra.search;

import com.novel.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把索引准备好。
 *
 * <p>和对象存储的初始化一样：**失败只报错不阻断启动**。理由是搜索不是主链路——
 * ES 没起来的时候，列表、详情、正文、书架、评分全都正常，只有搜索返回 10601。
 * 为了一个非核心依赖让整站起不来，是把可用性往下拉而不是往上抬。
 *
 * <p>{@code @Order(110)} 排在存储初始化（100）之后，纯粹是为了让启动日志的顺序稳定好读。
 */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "novel.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NovelIndexBootstrap implements ApplicationRunner {

    private final NovelIndexAdmin indexAdmin;
    private final SearchProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            indexAdmin.probeAnalyzer();
            indexAdmin.ensure();
        } catch (Exception e) {
            log.error("搜索索引初始化失败，搜索将返回 {}（{}）。原因：{}",
                    ResultCode.SEARCH_UNAVAILABLE.getCode(),
                    ResultCode.SEARCH_UNAVAILABLE.getMessage(),
                    e.getMessage());
            log.debug("搜索索引初始化失败详情", e);
        }
    }
}
