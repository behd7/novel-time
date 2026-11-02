package com.novel.api.controller;

import com.novel.common.result.Result;
import com.novel.infra.health.DependencyHealthChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统探针。M0 阶段的自检入口。
 */
@Tag(name = "系统", description = "健康检查与依赖体检")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final DependencyHealthChecker dependencyHealthChecker;

    @Operation(summary = "存活探针", description = "不查数据库，只看进程还活着，给 K8s liveness 用")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "novel");
        body.put("status", "UP");
        body.put("serverTime", OffsetDateTime.now().toString());
        return Result.ok(body);
    }

    @Operation(summary = "中间件依赖体检", description = "逐个探测 MySQL / Redis / RabbitMQ / ES / MinIO")
    @GetMapping("/dependencies")
    public Result<Map<String, Object>> dependencies() {
        return Result.ok(dependencyHealthChecker.check());
    }
}
