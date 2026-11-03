# AGENTS.md

本文件是本仓库对 AI 协作代理的硬性约定，优先级高于代理的默认习惯。

- **适用范围**：本仓库（`novel`）内的所有任务。
- **冲突处理**：本文件与 `README.md`、`docs/` 冲突时，事实以代码为准；流程以本文件为准。
- **维护要求**：改动工程结构、中间件拓扑、响应契约时，**同一个提交里**同步更新本文件对应章节。

---

## 一、任务汇报格式（强制）

**每一次任务汇报都必须按下面三段式输出**，缺一段视为汇报未完成。汇报跟随最终答复一起给出，不要另起文件、不要只在注释或提交信息里写。

**汇报是给人看的，不是给编译器看的。** 三段里最常写坏的是【实现路径】：把改动清单原样贴出来，类名一个不落，却没有一句"为什么这么做"。合格的汇报是**先用人话讲清思路与取舍，再把类名、队列名、配置项作为证据挂上去**。下面的反例与正例都是本仓库真实发生过的，照着正例的密度写。

> 一处格式差异：对话里的汇报三段标题齐全；写进 commit message 时，只有【任务结论】的标题换成裸结论句（见「汇报即提交」第 2 条）。不要因为写 commit，把对话里的标题也丢了。

### 【任务结论】

一句话说明完成了什么。要求：

- 一句话，不超过 40 字，动词开头（"完成了…" / "修复了…" / "删除了…"）。
- 必须点明**改动的对象**与**落点**，例如"完成了 RabbitMQ 消息队列在评论入库链路的接入"。
- 禁止空话："优化了代码结构""完善了相关功能""已按要求处理"一律不合格。

### 【实现路径】

**先说人话，再挂证据。** 固定四段，顺序不许调：

1. **整体思路**（3–5 句白话）：这次解决什么问题、为什么选这条路、最关键的一个取舍是什么。**这段里专有名词不超过 3 个**，出现时必须紧跟一句不超过 15 字的中文解释。
2. **关键组件**：一行一个对象，格式固定为 `` `名字`（**它是什么**）— **为什么这样定** ``。名字只报主干，其余折叠成一句话带过（"另有 3 个 DTO 和 2 个配置类"），不要逐个点名。
3. **发送方 / 接收方**：先用一句白话讲清数据从哪走到哪，再挂类名 + 方法 + 注解当证据。本次不涉及消息链路时，这两项**合并成一行**写 `发送方/接收方：不涉及（原因）`。
4. **失败处理**：先说"出错时系统或用户会看到什么"，再给机制参数（重试次数、退避方式、死信队列名、幂等键）。

#### 术语预算（硬指标）

- 每段最多引入 **3 个新术语**；首次出现必须带 ≤15 字解释，写成 `TokenPort`（发/验令牌的接口）这种形式。
- **单行反引号标识符 ≤ 3 个**，超了就拆行或改成叙述。
- **禁止标识符瀑布**：连续 3 行以上以反引号开头、且没有"为什么"的列表，直接判不合格。
- 一次任务真有 30 个类名要交代，也只报主干 5 个，其余折叠成一句——**汇报不是代码目录**。

#### 设计取舍（必写一条）

一句话讲清"为什么不是另一种做法"，格式：`选了 A，没选 B，因为 ——`。确实没有取舍空间时（纯文档、纯格式调整）写 `无取舍：<原因>`，不许留空。

#### 精度底线（不满足即视为不合格）

| 项目 | 必须写到 | 不合格写法 |
|---|---|---|
| 思路 | 一段话讲清"为什么这么解"，含被否掉的做法 | 开头直接罗列类名 |
| 组件 | `novel.event`（Topic）、`q.novel.search.sync`、`novel.search.#`，每个都带"为什么" | "配置了交换机" |
| 发送方 | `CommentService.create()` 落库后调 `RabbitTemplate.convertAndSend()` | "在业务层发送" |
| 接收方 | `CommentConsumer` 上 `@RabbitListener(queues = ...)` | "消费端处理" |
| 失败处理 | 3 次指数退避 → `q.novel.dead`，以 `commentId` 幂等 | "做了重试和幂等" |

#### 补充规则

- **不涉及的子项不许省略，也不许编造**，写 `不涉及：<原因>`。例如只改文档时写"发送方/接收方：不涉及（本次无消息链路改动）"。
- 一次任务若涉及多条链路（如评论 + 搜索同步），**每条链路各写一组**，不要合并成一段。
- 只写**实际存在**的东西。设计好但未实现的一律标注"（设计，未实现）"。
- 自检判据：**把汇报发给一个不熟悉本项目的人，他能否在 30 秒内说出"这次为什么这么改"**。说不出来就是名词堆砌，重写。

### 【验证方式】

如何确认接入生效，必须可执行或可观测，至少给出两类，且**至少一类是人话级的**——打开哪个地址看到什么、手工敲哪条命令得到什么结果。不能通篇是类名与用例数量。

- **命令/接口**：可直接复制的命令（`mvn test -Dtest=...`、`curl ...`）或接口地址，并写清"应该看到什么"。
- **日志/监控**：要看到什么才算成功，例如该 `traceId` 下出现哪两行日志、`/actuator/health` 中某组件 UP、队列深度为 0。
- **测试用例**：测试类/方法名 + **断言了什么**。只报"39 个用例全绿"不给断言的，不合格。

禁止写法："已验证通过""测试正常"这种没有证据的结论。

### 反例与正例（本项目的精度基准）

#### 反例：一次真实被否的汇报（M1 认证）

> - 创建了 core 侧五个端口：`UserRepository`、`TokenPort`（含 `Type{ACCESS,REFRESH}`、`Issued`、`Claims` 三个嵌套类型）、`RefreshTokenStore`、`PasswordHasher`、`LoginAttemptLimiter`；应用服务 `AuthService`（`register` / `login` / `refresh` / `logout`）与 `UserService`。
> - 创建了 infra 侧适配器：`JwtTokenAdapter`（jjwt 0.12.6，HS 算法按密钥长度自动选择）……统一由 `SecurityInfraConfig` 装配。

**问题**：信息都对，但没有一句解释"为什么要有 `TokenPort`""为什么 refresh 要落 Redis"。读者拿到 38 个类名，仍然不知道这一版的设计意图和取舍。这是**目录**，不是**汇报**。

#### 正例一：同一件事的白话版（认证接入，M1）

> **【实现路径】**
>
> - **整体思路**：认证最容易把技术细节漏进业务代码——`AuthService` 里只要出现 JWT、BCrypt、Redis 这些词，以后换算法就得改业务逻辑，也没法脱离中间件做单测。所以这版只做一件事：把"发令牌""存续期""验密码""限频次"四件事定义成 core 的接口，具体技术全部推到 infra。第二个取舍是 refresh 没做成纯无状态 JWT：纯 JWT 签出去就作废不了，用户点"退出登录"其实退不掉。改成"令牌带编号 + Redis 存白名单"，登出就删白名单，旧令牌立刻失效——代价是每次刷新多一次 Redis 查询，换来的是能真正踢人。
> - **关键组件**：
>   - `TokenPort`（发/验令牌的接口）— core 里只声明"签发一对令牌""验一个令牌"，看不见 JWT 字样，换实现不动业务代码
>   - `JwtTokenAdapter`（其实现，jjwt）— 密钥不足 32 字节直接启动失败，因为带着弱密钥跑起来比起不来更危险
>   - `RefreshTokenStore`（refresh 白名单）— 键 `auth:refresh:{jti}`、值 userId，TTL 与令牌同寿，登出/轮换即删
>   - `BCryptPasswordHasher` — strength=10，单次 50~100ms，够挡离线爆破又不拖垮登录接口
>   - `LoginAttemptLimiter` — 键 `auth:login:ip:{ip}`，60 秒 5 次，Lua 保证计数与过期原子
>   - 另有 3 个实体、3 个 Mapper 与 6 个出入参对象，都是照 V1 的表结构直译，无特殊处理
> - **发送方/接收方**：不涉及（本次无消息链路，`novel.event` 拓扑与 `outbox` 表未动）。
> - **失败处理**：用户看到的永远是同一份 `{code,message,traceId}` JSON，不会因为错在过滤器还是错在 Controller 而变成 HTML 错误页——过滤器链上的 401/403 由 `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` 直接写响应，Controller 内被 `@PreAuthorize` 拒绝的走 `GlobalExceptionHandler`。令牌过期、伪造、类型不符统一转 `10105`，不让 `ExpiredJwtException` 冒到 500；登录失败一律 `10103`，且用户不存在时也跑一次 BCrypt，抹平响应时间差，避免被用来枚举账号。
>
> **【验证方式】**
> - 人话级：不带令牌请求 `GET /api/v1/users/me`，应看到 401 且响应体是 `{"code":40100,…}`；带上刚登录拿到的令牌再请求，应看到 200 和自己的昵称。
> - `mvn test` → 39 个用例全绿；其中 `AuthFlowIntegrationTest` 用 Testcontainers 起 MySQL + Redis 跑完整链路，断言"旧 refresh 重放返回 `10105`"。
> - Redis 侧 `docker exec novel-redis redis-cli --scan --pattern 'auth:*'` 应看到 `auth:refresh:{jti}` 与 `auth:login:ip:127.0.0.1`。

#### 正例二：小改动的密度（MQ 接入）

> **【实现路径】**
> - **整体思路**：评论写入和搜索索引必须解耦，否则 ES 一抖动就拖垮发评论。但"落库后直接发 MQ"会丢消息——事务还没提交，broker 就可能是断的。所以先在同一个事务里写一条 `outbox` 记录，再由后台任务投递：事务保证"库里既然有评论，事件就不会丢"。
> - **关键组件**：`novel.event`（Topic，业务事件入口）绑 `q.novel.search.sync`（索引同步队列，键 `novel.search.#`）；`novel.dlx`（Direct）把坏消息收进 `q.novel.dead`（键 `dead`）。队列名带 `q.novel.` 前缀、路由键带 `novel.` 前缀，是为了在控制台一眼认出本项目的资源。
> - **发送方**：`CommentService.create()` 在评论落库的同一事务写 `outbox`（`message_key` 唯一），`OutboxRelay` 定时轮询 `status=0` 后调 `RabbitTemplate.convertAndSend()` 异步投递，开启 `publisher-confirm-type: correlated`；确认失败则 `retry_count+1` 并推迟 `next_retry_at`。
> - **接收方**：`CommentConsumer` 用 `@RabbitListener(queues = "q.novel.search.sync")` 监听，`acknowledge-mode: manual`，消费后 upsert 索引再手动 ack。
> - **失败处理**：消费异常先按 3 次指数退避（1s/2s/4s）重试，仍失败则靠 `default-requeue-rejected: false` 送进 `q.novel.dead` 留现场，由补偿任务重放，而不是在客户端无限 requeue 把 CPU 打满；消费端先插 `mq_consume_log(message_key, consumer)`，唯一键冲突即跳过，以此幂等。
>
> **【验证方式】**
> - 人话级：发一条评论后打开 `http://127.0.0.1:15672` 的队列页，`q.novel.search.sync` 应出现一条入队随即归零，`q.novel.dead` 应为 0。
> - 日志：该 `traceId` 下先出现 `outbox 已写入`，再出现 `search sync consumed`。
> - `mvn test -Dtest=OutboxRelayTest` 断言确认失败的消息 `retry_count` 递增且不会被重复投递。

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

现状（M0 骨架 + M1 认证 + M2 阅读）：

```
com.novel.common
├── exception/BizException
└── result/{Result, ResultCode, PageQuery, PageResult}

com.novel.core
├── storage/{StoragePort, FileService, FileBizType, PresignedUpload}
├── user/
│   ├── domain/{UserAccount, UserProfile, UserStat}
│   ├── port/{UserRepository, TokenPort, RefreshTokenStore, PasswordHasher, LoginAttemptLimiter}
│   └── application/{AuthService, UserService, RegisterCommand, LoginCommand,
│                     ProfileUpdate, AuthView, UserView, UserDetailView}
├── novel/
│   ├── domain/{Novel, Chapter, Category}
│   ├── port/{NovelRepository, ChapterRepository, NovelCache, NovelViewCounter}
│   ├── application/{NovelService, NovelQuery, NovelSort, view/*}
│   └── importing/{TxtNovelParser, ParsedNovel, ParsedChapter, NovelImportService}
└── reading/
    ├── domain/BookshelfItem
    ├── port/BookshelfRepository
    └── application/{BookshelfService, view/BookshelfView}

com.novel.infra
├── config/{MybatisPlusConfig, RedisConfig, RabbitConfig, StorageConfig, SecurityInfraConfig}
├── persistence/{UserRepositoryImpl, NovelRepositoryImpl, ChapterRepositoryImpl,
│                BookshelfRepositoryImpl, entity/*, mapper/*}
├── security/{JwtTokenAdapter, JwtProperties, BCryptPasswordHasher,
│             RedisTokenStore, RedisLoginAttemptLimiter, AuthRateLimitProperties}
├── cache/{RedisNovelCache, RedisNovelViewCounter}
├── job/ViewCountFlushJob
├── storage/{S3StorageAdapter, StorageProperties}
└── health/DependencyHealthChecker

com.novel.api
├── NovelApplication                        # @SpringBootApplication(scanBasePackages = "com.novel")，排除 UserDetailsServiceAutoConfiguration
├── config/{OpenApiConfig, StorageBootstrap, SecurityConfig, SchedulingConfig}
├── controller/{AuthController, UserController, NovelController, CategoryController,
│               BookshelfController, FileController, SystemController, dto/*}
├── importing/NovelImportRunner             # @Profile("import")，导完 System.exit
├── security/{JwtAuthenticationFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler,
│             SecurityResponses, LoginUser, SecurityUtils}
└── web/{TraceIdFilter, ResponseTraceAdvice, GlobalExceptionHandler}
```

**认证的端口倒置**：`TokenPort` / `RefreshTokenStore` / `PasswordHasher` / `LoginAttemptLimiter` 四个接口定义在 core，JJWT、Redis、BCrypt 的实现全部在 infra。core 里没有任何一行代码知道"JWT"这个词。

`JwtAuthenticationFilter` **不带 `@Component`**，由 `SecurityConfig` 手动 new 进过滤器链 —— 标了 `@Component` 会被 Spring Boot 当普通 Servlet Filter 再注册一次，导致每个请求执行两遍。

目标结构（后续里程碑按业务域横向铺开，每域三层 `application / domain / port`）：`novel`、`reading`、`review`、`community`、`search`、`rank`。

### 2.5 请求链路

```
Client
  → TraceIdFilter（traceId 入 MDC + 响应头 X-Trace-Id）
  → Spring Security 过滤器链
      ├─ 放行：/api/v1/auth/**、/api/v1/system/**、/swagger-ui/**、/v3/api-docs/**、/actuator/health
      ├─ JwtAuthenticationFilter：解析 Bearer → SecurityContext(LoginUser)
      └─ 失败：RestAuthenticationEntryPoint(401) / RestAccessDeniedHandler(403)，响应体与 Result 同构
  → Controller（@Validated 入参校验）
  → ApplicationService（core，业务规则）
      ├─ MySQL        MyBatis-Plus（分页/乐观锁/防全表攻击三插件）
      ├─ Redis        缓存 / 榜单 ZSET / 计数 / refresh 白名单 / 登录限流
      ├─ MinIO        预签名 URL（后端不碰文件流）
      ├─ ES           只读搜索
      └─ Outbox       写库同事务落 outbox 表 → 异步投递 RabbitMQ → 消费者
  → ResponseTraceAdvice（补 traceId 进 Result）
  → GlobalExceptionHandler（兜底）
```

放行规则有顺序陷阱：`GET /api/v1/users/*` 会匹配到 `/users/me`，所以 `.requestMatchers(GET, "/api/v1/users/me").authenticated()` 必须写在 `permitAll` 之前。


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

已占用的键空间：

| 键 | 类型 | TTL | 用途 |
|---|---|---|---|
| `auth:refresh:{jti}` | String → userId | 与 refresh 令牌同寿（7d） | refresh 白名单，登出/轮换即删 |
| `auth:login:ip:{ip}` | String 计数 | 60s | 登录限流，Lua 保证 INCR+EXPIRE 原子 |
| `novel:chapter:text:{chapterId}` | String | 2h | 章节正文缓存；`chapter_content` 是 MEDIUMTEXT，命中最省事 |
| `novel:categories` | String(JSON) | 10m | 分类树，读多写零；导入完成时主动失效 |
| `novel:view:delta` | Hash(field=novelId) | 无 | 阅读数增量，`ViewCountFlushJob` 每 60s 取出并 `HINCRBY` 回库 |

> 小说详情**不缓存**：主键查询 1ms，为它维护失效逻辑是负优化。缓存只留收益明确的两处（正文、分类）与一个写放大点（阅读数）。

> 本机 6379 的主机 Redis 时常不在线，`deploy/docker-compose.yml` 里提供了 `redis` 兜底服务（映射 **6380**，避开主机端口），需要时 `docker compose up -d redis`，并在 `application-local.yml` 里写 `spring.data.redis.port: 6380`。

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
- 密码只存 BCrypt 哈希（strength=10，`$2a$10$` 前缀）。
- **401/403 有两套出口，但必须是同一份 JSON**：过滤器链上的由 `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` 直接写响应（此时 `@RestControllerAdvice` 还没介入）；Controller 内 `@PreAuthorize` 拒绝的由 `GlobalExceptionHandler` 处理。两者都输出 `Result{code,message,traceId}`，401 对应 `40100`，403 对应 `40300`。
- 登录失败一律返回 `10103 用户名或密码错误`，不区分"用户不存在"与"密码错误"。

### 2.9 配置与环境变量

`application.yml` 可提交；密码只进 `application-local.yml`（已被 `.gitignore` 忽略，仓库只留 `.example`），通过 `spring.config.import: optional:classpath:application-local.yml` 加载。

常用环境变量：`NOVEL_DB_HOST/PORT/NAME/USER/PASSWORD`、`NOVEL_REDIS_HOST/PORT/PASSWORD/DB`、`NOVEL_MQ_HOST/PORT/USER/PASSWORD`、`NOVEL_ES_URI`、`NOVEL_MINIO_ENDPOINT/PUBLIC_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET`、`NOVEL_JWT_SECRET`、`NOVEL_SERVER_PORT`。

`novel.jwt.secret` 是**唯一没有默认值**的配置项：为空或不足 32 字节时 `JwtProperties.validate()` 直接抛 `IllegalStateException`，应用起不来。这是刻意的——带着弱密钥跑起来比起不来危险得多。生成命令：`openssl rand -base64 48`。

其他配置项：`novel.jwt.access-ttl`(30m) / `refresh-ttl`(7d) / `issuer`(novel)、`novel.auth.rate-limit.login-max-attempts`(5) / `login-window`(60s)、`novel.reading.view-flush-interval-ms`(60000)、`novel.import.dir`(deploy/samples)。

### 2.10 常用命令

```bash
# 中间件（ES / MinIO / Redis 兜底 走 Docker；MySQL、RabbitMQ 用 Windows 主机实例，WSL mirrored 网络直连 127.0.0.1）
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml up -d redis   # 只起 Redis（端口 6380）

# 启动
mvn -pl novel-api -am spring-boot:run
mvn -DskipTests package && java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar

# 导入小说（不起 Web，导完 System.exit；同名书自动跳过，可重复执行）
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=import --novel.import.dir=deploy/samples

# 测试
mvn test                                  # 55 个用例，含 Testcontainers（需 Docker）
mvn test -Dtest=ArchitectureTest          # 只跑架构约束
mvn test -Dtest=SchemaMigrationTest       # 只跑 Flyway 迁移
mvn test -Dtest=AuthFlowIntegrationTest   # 认证链路端到端
mvn test -Dtest=AuthServiceTest           # 认证业务逻辑单元测试
mvn test -Dtest=ReadingFlowIntegrationTest # 导入→列表→正文→书架端到端
mvn test -Dtest=TxtNovelParserTest        # TXT 解析器纯单测
```

自检入口：`/swagger-ui.html`、`/api/v1/system/ping`、`/api/v1/system/dependencies`（五项中间件实测）、`/actuator/health`。

### 2.11 里程碑

- [x] **M0 骨架**：四模块工程、Flyway 21 表、统一响应、全局异常、traceId、Swagger、中间件体检、预签名上传闭环
- [x] **M1 用户与认证**：注册/登录/刷新/登出、JWT 双令牌 + Redis 白名单、Spring Security 过滤器链、401/403 统一响应、登录限流、个人资料读写、他人主页免登录
- [x] **M2 阅读**：TXT 解析与批量导入（幂等）、小说列表/详情、目录分页、正文 + Redis 缓存、阅读数写缓冲、书架（收藏 + 进度 + 有新章标记）
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
