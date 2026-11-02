package com.novel.api.config;

import com.novel.core.storage.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 bucket 存在。
 *
 * <p>为什么不在构造器里做：对象存储是外部依赖，它挂了不应该导致整个应用起不来。
 * 这里吞掉异常只打 warn，应用照常启动，只是上传功能不可用，等存储恢复后再重启即可。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class StorageBootstrap implements ApplicationRunner {

    private final StoragePort storagePort;

    @Override
    public void run(ApplicationArguments args) {
        try {
            storagePort.ensureBucket();
        } catch (Exception e) {
            log.warn("对象存储初始化失败，上传功能不可用：{}", e.getMessage());
        }
    }
}
