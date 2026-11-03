# AGENTS.md

本文件是本仓库对 AI 协作代理的硬性约定，优先级高于代理的默认习惯。

- **适用范围**：本仓库（`novel`）内的所有任务。
- **冲突处理**：本文件与 `README.md`、`docs/` 冲突时，事实以代码为准；流程以本文件为准。
- **维护要求**：改动工程结构、中间件拓扑、响应契约时，**同一个提交里**同步更新本文件对应章节。

---

## 一、任务汇报格式（强制）

**每一次任务汇报都必须按下面三段式输出**，缺一段视为汇报未完成。汇报跟随最终答复一起给出，不要另起文件、不要只在注释或提交信息里写。

### 【任务结论】

一句话说明完成了什么。要求：

- 一句话，不超过 40 字，动词开头（"完成了…" / "修复了…" / "删除了…"）。
- 必须点明**改动的对象**与**落点**，例如"完成了 RabbitMQ 消息队列在评论入库链路的接入"。
- 禁止空话："优化了代码结构""完善了相关功能""已按要求处理"一律不合格。

### 【实现路径】

拆到**可验证的对象级**。必须覆盖下面四个子项，逐条列：

- **创建了哪些关键组件**：交换机名与类型、队列名、绑定关系、路由键、配置项、类/接口/方法、数据表与索引、接口路径。名字要写**全名原样**，不要写"相关配置"。
- **发送方**：在哪个环节（哪个类、哪个方法、落库前还是落库后）、调用什么方法、以什么方式发送（同步/异步）、确认机制（publisher confirm / 事务消息 / Outbox）。
- **接收方**：在哪里监听（类名 + 注解）、消费哪个队列、消费后做什么（写库/写 ES/更新 ZSET）。
- **失败处理**：重试策略（次数 + 退避方式）、死信队列名、补偿/回滚、幂等如何保证（靠什么键）。

精度底线（不满足即视为不合格）：

| 项目 | 必须写到 | 不合格写法 |
|---|---|---|
| 组件 | `novel.event`（Topic）、`q.novel.search.sync`、`novel.search.#` | "配置了交换机" |
| 发送方 | `CommentService.create()` 落库后调 `RabbitTemplate.convertAndSend()` | "在业务层发送" |
| 接收方 | `CommentConsumer` 上 `@RabbitListener(queues = ...)` | "消费端处理" |
| 失败处理 | 3 次指数退避 → `q.novel.dead`，以 `commentId` 幂等 | "做了重试和幂等" |

补充规则：

- **不涉及的子项不许省略，也不许编造**，写 `不涉及：<原因>`。例如只改文档时写"发送方：不涉及（本次无消息链路改动）"。
- 一次任务若涉及多条链路（如评论 + 搜索同步），**每条链路各写一组**，不要合并成一段。
- 只写**实际存在**的东西。设计好但未实现的一律标注"（设计，未实现）"。

### 【验证方式】

如何确认接入生效，必须可执行或可观测，至少给出两类：

- **命令/接口**：可直接复制的命令（`mvn test -Dtest=...`、`curl ...`）或接口地址。
- **日志/监控**：要看到什么才算成功，例如控制台出现 `traceId`、`/actuator/health` 中某组件 UP、队列深度为 0。
- **测试用例**：测试类/方法名，以及它断言了什么。

禁止写法："已验证通过""测试正常"这种没有证据的结论。

### 示例（本项目的精度基准）

> 本次任务完成了 RabbitMQ 消息队列在评论入库链路的接入：
>
> **【实现路径】**
> - 创建了 `novel.event`（Topic 类型）和 `q.novel.search.sync`，以 `novel.search.#` 为路由键绑定；死信侧新增 `q.novel.dead`，由 `novel.dlx`（Direct）以 `dead` 键投递。
> - 发送方：评论服务 `CommentService.create()` 在评论落库的同一事务内写入 `outbox` 表（`message_key` 唯一），由 `OutboxRelay` 定时轮询 `status=0` 的记录后调 `RabbitTemplate.convertAndSend()` 异步投递，开启 `publisher-confirm-type: correlated` 确认投递，确认失败则 `retry_count+1` 并推迟 `next_retry_at`。
> - 接收方：`CommentConsumer` 用 `@RabbitListener(queues = "q.novel.search.sync")` 监听，`acknowledge-mode: manual`，消费后 upsert 检索库并向 broker 手动 ack。
> - 失败处理：消费异常按 3 次指数退避（1s/2s/4s）重试，仍失败则 `default-requeue-rejected: false` 使其经 `novel.dlx` 进入 `q.novel.dead`，由补偿任务人工/定时重放；消费端先插入 `mq_consume_log(message_key, consumer)`，唯一键冲突即跳过，以此保证幂等。
>
> **【验证方式】**
> - 发一条评论后 `curl http://127.0.0.1:15672/api/queues` 可见 `q.novel.search.sync` 入队又归零；
> - 日志中该 `traceId` 下同时出现 `outbox 已写入` 与 `search sync consumed`，且 `/api/v1/system/dependencies` 中 `rabbitmq.ok=true`；
> - `mvn test -Dtest=OutboxRelayTest` 断言确认失败的消息 `retry_count` 递增且不重复投递。

### 汇报即提交（强制）

**每个任务结束时，在给出汇报的同时执行一次 Git 提交，提交信息就是这份汇报正文。**

1. **暂存**：只 `git add` 本次任务实际改动的路径，**显式列文件名**。禁止 `git add -A` / `git add .`——那会把无关的半成品和本地配置一起卷进提交；`git status --short` 复核无误后再提交。
2. **提交信息 = 汇报正文，但首行是纯结论句**：首行只写【任务结论】的**那句话本身**，**不带** `【任务结论】` 标题；空一行后接 `【实现路径】` 与 `【验证方式】` 两段（标题和内容照抄汇报）。这样 `git log --oneline` 一眼看到的就是结论，而不是 `【任务结论】 完成了…` 这种带标题的截断行。不加 `docs:` / `feat:` 之类前缀，不写"本次提交…"的额外流水账；结论句受本文件【任务结论】的 40 字上限约束，保证首行不会被 Git 截断。多行信息用 `git commit -F -` + heredoc 传入，不要用多个 `-m` 拼接：

   ```bash
   git add AGENTS.md novel-api/src/main/java/...
   git commit -F - <<'MSG'
   一句话说明完成了什么。

   【实现路径】
   - 创建了 …

   【验证方式】
   - 命令 / 日志 / 测试用例
   MSG
   ```
3. **一次任务一个提交**，且**先验证再提交**：跑完【验证方式】里的检查才提交；验证没过就不提交。
4. **回填 hash**：提交成功后，把 `git log -1 --stat` 得到的 short hash 追加到汇报【验证方式】的末尾。commit message 里不写 hash（提交时还不知道）。
5. **失败不掩盖**：提交失败（缺 `user.email`、钩子拦截、无可提交改动）时，汇报里必须写 `提交未执行：<原因>`，不得假装已提交。
6. **历史只增不改**：已推送的提交禁止 `amend` / `rebase` / `push --force`；要修正就追加新提交。**未经明确要求不要 `git push`**——提交留在本地。

---

## 二、项目架构（当前状态）

### 2.1 定位

小说阅读 + 社区平台后端，**模块化单体（Modular Monolith）**：单进程部署，模块间靠 Maven 依赖方向与 ArchUnit 约束隔离，为将来拆分留口子。

### 2.2 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 语言 / 构建 | Java 17 + Maven 多模块 | Spring Boot 3.3.5，父 POM 统一版本 |
| 主库 | MySQL 8.0 | 正文也在库里，不放对象存储 |
| 缓存 / 榜单 / 计数 | Redis | Lettuce 连接池，ZSET + 计数 |
| 消息 | RabbitMQ 4.x | Outbox 模式，配死信队列 |
| 搜索 | Elasticsearch 8.13 | IK 分词、BM25、高亮；**MySQL 才是真相源** |
| 对象存储 | MinIO（S3 协议） | 封面/头像/导入原文件，预签名直传 |
| 持久层 | MyBatis-Plus 3.5.7 + Flyway | SQL 可控，schema 版本化 |
| 文档 / 监控 | SpringDoc OpenAPI 3 + Actuator/Micrometer | Swagger 即演示界面 |

### 2.3 模块与依赖方向

```
novel/                      聚合 POM（pom.xml）
├── novel-common/           统一响应 Result<T>、错误码、分页、BizException   （最底层，不依赖任何业务模块）
├── novel-core/             领域模型 + 应用服务 + 端口接口（不引 web/mybatis/redis/es）
├── novel-infra/            MyBatis、Redis、MQ、ES、MinIO、健康探针的适配器
└── novel-api/              Controller、全局异常、traceId、配置、启动类（装配根）
```

依赖方向：`novel-api → novel-core ← novel-infra`，`novel-common` 被三者依赖。

**这条方向由 `novel-api/src/test/java/com/novel/api/ArchitectureTest.java` 强制**，共 4 条规则，违规即构建失败：

1. `com.novel.core..` 不得依赖 `com.novel.infra..` / `com.novel.api..`；
2. `com.novel.infra..` 不得依赖 `com.novel.api..`；
3. `com.novel.common..` 不得依赖 core/infra/api；
4. `@RestController` 只能出现在 `com.novel.api..`。

> 新增中间件能力时的正确姿势：core 里定 `port` 接口 → infra 里写 adapter → api 里装配。**不许**为了少写一个转换类让 core 直接 import Mapper。

### 2.4 包结构

现状（M0）只有一条完整纵向切片：

```
com.novel.common
├── exception/BizException
└── result/{Result, ResultCode, PageQuery, PageResult}

com.novel.core
└── storage/{StoragePort, FileService, FileBizType, PresignedUpload}

com.novel.infra
├── config/{MybatisPlusConfig, RedisConfig, RabbitConfig, StorageConfig}
├── storage/{S3StorageAdapter, StorageProperties}
└── health/DependencyHealthChecker

com.novel.api
├── NovelApplication                        # @SpringBootApplication(scanBasePackages = "com.novel")
├── config/{OpenApiConfig, StorageBootstrap}
├── controller/{FileController, SystemController, dto/PresignRequest}
└── web/{TraceIdFilter, ResponseTraceAdvice, GlobalExceptionHandler}
```

目标结构（M1 起按业务域横向铺开，每域三层 `application / domain / port`）：`user`、`novel`、`reading`、`review`、`community`、`search`、`rank`。

### 2.5 请求链路

```
Client
  → TraceIdFilter（traceId 入 MDC + 响应头 X-Trace-Id）
  → [M1 起] Spring Security JWT 过滤器链
  → Controller（@Validated 入参校验）
  → ApplicationService（core，业务规则）
      ├─ MySQL        MyBatis-Plus（分页/乐观锁/防全表攻击三插件）
      ├─ Redis        缓存 / 榜单 ZSET / 计数
      ├─ MinIO        预签名 URL（后端不碰文件流）
      ├─ ES           只读搜索
      └─ Outbox       写库同事务落 outbox 表 → 异步投递 RabbitMQ → 消费者
  → ResponseTraceAdvice（补 traceId 进 Result）
  → GlobalExceptionHandler（兜底）
```

### 2.6 中间件拓扑（已声明的实际配置）

**RabbitMQ**（`novel-infra/.../config/RabbitConfig.java`）

| 资源 | 名称 | 说明 |
|---|---|---|
| 业务交换机 | `novel.event`（Topic，durable） | 三个业务队列的投递入口 |
| 死信交换机 | `novel.dlx`（Direct，durable） | 坏消息的统一收容处 |
| 队列 | `q.novel.search.sync` | 小说/章节变更后同步 ES 索引 |
| 队列 | `q.novel.rank.agg` | 阅读/收藏行为累加榜单 ZSET |
| 队列 | `q.novel.stat.agg` | 用户统计、评分聚合自愈 |
| 死信队列 | `q.novel.dead` | 人工/定时重放 |
| 路由键 | `novel.search.#` / `novel.rank.#` / `novel.stat.#` / `dead` | 前三个绑 `novel.event`，`dead` 绑 `novel.dlx` |
| 序列化 | `Jackson2JsonMessageConverter` | 禁止 JDK 序列化 |
| 消费参数 | `acknowledge-mode: manual`、`prefetch: 20`、`concurrency: 2..8`、`default-requeue-rejected: false` | 失败不无限 requeue，直接进死信 |
| 生产参数 | `publisher-confirm-type: correlated`、`publisher-returns: true`、`template.mandatory: true` | 配合 Outbox 保证不丢 |

> 现状：**拓扑已声明，生产者/消费者尚未实现**（M4 随搜索同步落地）。`outbox`、`mq_consume_log` 两张表已建好，是幂等与自愈的落点。

**Redis**（`RedisConfig`）：`StringRedisTemplate` + 自定义 `RedisTemplate<String,Object>`，key 用 `StringRedisSerializer`，value 用 `GenericJackson2JsonRedisSerializer`（JSON 里带 `@class`）。禁止 JDK 序列化——`redis-cli` 要能直接看懂缓存内容。

**对象存储**（`StorageConfig` → `S3StorageAdapter`）：AWS SDK 走 S3 协议，`pathStyleAccessEnabled(true)`（MinIO 必需），`S3Client` 与 `S3Presigner` 的 endpoint/region/凭证必须完全一致。`StorageBootstrap`（`@Order(100)` 的 `ApplicationRunner`）启动时幂等建 bucket 并设公共读策略，失败只打 warn 不影响启动。

**MySQL / Flyway**：迁移脚本在 `novel-api/src/main/resources/db/migration`，`V1__init_schema.sql` + `V2__seed_categories.sql`，共 21 张业务表 + `flyway_schema_history`。MyBatis-Plus 拦截器顺序固定为 **分页 → 乐观锁 → 防全表攻击**，分页 `maxLimit=50`。

**ES**：目前仅 `DependencyHealthChecker` 探活，索引与查询未实现（M4）。

### 2.7 数据模型（21 张表）

| 域 | 表 |
|---|---|
| 用户 | `user`、`user_profile`、`user_stat` |
| 小说 | `novel`、`novel_category`、`novel_tag`、`novel_tag_rel`、`chapter`、`chapter_content` |
| 阅读 | `bookshelf`、`bookshelf_group` |
| 评分评论 | `novel_rating`（`uk_novel_user` 一人一票）、`novel_review`、`review_reply`、`like_record` |
| 社区 | `post`、`post_novel_ref`（正文存 `[[ref:N]]` 占位符 + 结构化引用）、`post_comment` |
| 榜单 | `novel_rank`（`uk_period_date_novel`） |
| 基础设施 | `outbox`（`uk_message_key`）、`mq_consume_log`（`uk_msg_consumer`） |

### 2.8 响应与错误码契约

- 统一响应 `Result<T>{code, message, data, traceId, success}`，`code=0` 成功；`success` 由 `isSuccess()` 序列化而来，与 `code==0` 等价。
- 分层策略：**业务失败 → HTTP 200 + 业务码**；参数/协议错误 → 4xx；未捕获异常 → 500 且必须打完整堆栈。
- 错误码分段：`0` 成功；`4xxxx` 客户端（与 HTTP 对齐）；`5xxxx` 服务端；`1xxxx` 业务按域（1-1 用户 / 1-2 小说 / 1-4 评分评论 / 1-5 社区 / 1-6 搜索）。
- 分页入参 `page/size`，`size` 硬上限 50（`PageQuery.MAX_SIZE` 与分页插件双保险）；出参 `PageResult{list,total,page,size,hasNext}`。
- 时间统一 `Asia/Shanghai`，列类型 `DATETIME`（不用 `TIMESTAMP`）。
- 密码只存 BCrypt 哈希。

### 2.9 配置与环境变量

`application.yml` 可提交；密码只进 `application-local.yml`（已被 `.gitignore` 忽略，仓库只留 `.example`），通过 `spring.config.import: optional:classpath:application-local.yml` 加载。

常用环境变量：`NOVEL_DB_HOST/PORT/NAME/USER/PASSWORD`、`NOVEL_REDIS_HOST/PORT/PASSWORD/DB`、`NOVEL_MQ_HOST/PORT/USER/PASSWORD`、`NOVEL_ES_URI`、`NOVEL_MINIO_ENDPOINT/PUBLIC_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET`、`NOVEL_SERVER_PORT`。

### 2.10 常用命令

```bash
# 中间件（ES / MinIO 走 Docker；MySQL/Redis/MQ 用 Windows 主机上的实例，WSL mirrored 网络直连 127.0.0.1）
docker compose -f deploy/docker-compose.yml up -d

# 启动
mvn -pl novel-api -am spring-boot:run
mvn -DskipTests package && java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar

# 测试
mvn test                                  # 含 Testcontainers（需 Docker）
mvn test -Dtest=ArchitectureTest          # 只跑架构约束
mvn test -Dtest=SchemaMigrationTest       # 只跑 Flyway 迁移
```

自检入口：`/swagger-ui.html`、`/api/v1/system/ping`、`/api/v1/system/dependencies`（五项中间件实测）、`/actuator/health`。

### 2.11 里程碑

- [x] **M0 骨架**：四模块工程、Flyway 21 表、统一响应、全局异常、traceId、Swagger、中间件体检、预签名上传闭环
- [ ] M1 用户与认证（JWT + Spring Security）
- [ ] M2 小说/章节/导入/阅读/书架
- [ ] M3 评分与书评
- [ ] M4 搜索（ES + IK + Outbox 同步 + 消费者）
- [ ] M5 榜单
- [ ] M6 社区
- [ ] M7 工程收尾（压测 / CI / 架构图）

---

## 三、改动时的硬约束

1. **不许破坏依赖方向**：三方源码都要过 `ArchitectureTest`；新中间件能力必须走 core 的 `port` 接口。
2. **SQL 迁移脚本必须 LF**：`.gitattributes` 已锁 `eol=lf`，行尾变化会让 Flyway checksum 不匹配。已执行的迁移脚本**禁止修改**，只能新增 `V{n}__xxx.sql`。
3. **禁止 JDK 序列化**：Redis 用 JSON，MQ 用 `Jackson2JsonMessageConverter`。
4. **禁止在事务里直接发 MQ**：写库与事件写 `outbox` 同事务，由投递任务异步发出；消费者以 `mq_consume_log` 唯一键做幂等。
5. **密码/密钥不进 `application.yml`**：走 `application-local.yml` 或环境变量。
6. **`size` 上限 50**：新分页接口必须用 `PageQuery`，不得放开限制。
7. **不要主动拉新的中间件 SDK**：MinIO 走 S3 协议（AWS SDK），ES 客户端版本以 Boot BOM 为准。
