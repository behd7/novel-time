# AGENTS.md

本文件是本仓库对 AI 协作代理的硬性约定，优先级高于代理的默认习惯。

- **适用范围**：本仓库（`novel`）内的所有任务。
- **冲突处理**：本文件与 `README.md`、`docs/` 冲突时，事实以代码为准；流程以本文件为准。
- **维护要求**：改动工程结构、中间件拓扑、响应契约时，**同一个提交里**同步更新本文件对应章节。

---

## 一、任务汇报格式（强制）

**每一次任务汇报都必须按下面四段输出**，缺一段视为汇报未完成：**任务结论 → 实现路径 → 改动文件 → 验证方式**。汇报跟随最终答复一起给出，不要另起文件、不要只在注释或提交信息里写。

**汇报是给人看的，不是给编译器看的。** 四段里最常写坏的是【实现路径】：把改动清单原样贴出来，类名一个不落，却没有一句"为什么这么做"。合格的汇报是**先用人话讲清思路与取舍，再把类名、队列名、配置项作为证据挂上去**。下面的反例与正例都是本仓库真实发生过的，照着正例的密度写。

> 一处格式差异：对话里的汇报四段标题齐全；写进 commit message 时，只有【任务结论】的标题换成裸结论句（见「汇报即提交」第 2 条）。不要因为写 commit，把对话里的标题也丢了。

### 【任务结论】

一句话说明完成了什么。要求：

- 一句话，不超过 40 字，动词开头（"完成了…" / "修复了…" / "删除了…"）。
- 必须点明**改动的对象**与**落点**，例如"完成了 RabbitMQ 消息队列在评论入库链路的接入"。
- 禁止空话："优化了代码结构""完善了相关功能""已按要求处理"一律不合格。

### 【实现路径】

**先说人话，再挂证据。** 固定四段，顺序不许调：

1. **整体思路**（3–5 句白话）：这次解决什么问题、为什么选这条路、最关键的一个取舍是什么。**这段里专有名词不超过 3 个**，出现时必须紧跟一句不超过 15 字的中文解释。段末用一行列出**本次完成了哪些功能**（3–6 个，顿号分隔），它是下一段的纲。
2. **逐功能实现思路**（本格式的核心）：按上一条列出的顺序，**一个功能一段**，段首用粗体功能名。每段写两件事——这个功能**怎么做的**（流程或规则），以及**为什么这么做**（防住哪类问题、付出了什么代价）。功能内部的取舍就写在这一段里，不另起段落。
3. **关键组件**：一行一个对象，格式固定为 `` `名字`（**它是什么**）— **为什么这样定** ``。名字只报主干，其余折叠成一句话带过（"另有 3 个 DTO 和 2 个配置类"）。逐功能里已经解释清楚的理由，这里一句话收住，不重复展开。
4. **失败处理**：先说"出错时系统或用户会看到什么"，再给机制参数（重试次数、退避方式、死信队列名、幂等键）。

> 为什么删掉了"发送方/接收方"：这两项是为消息链路量身定的，非 MQ 任务里永远只剩两句"不涉及"。消息链路用**逐功能**讲更顺——一个功能就是"一次投递 + 一次消费 + 一处兜底"，不需要额外的固定栏目。

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
| 思路 | 一段话讲清"为什么这么解"，含被否掉的做法与功能清单 | 开头直接罗列类名 |
| 功能 | 每个功能一段：怎么做的 + 为什么这么做 + 其中取舍 | "完成了注册登录功能" |
| 组件 | `novel.event`（Topic）、`q.novel.search.sync`、`novel.search.#`，每个都带"为什么" | "配置了交换机" |
| 文件 | 每个改动文件一行，标新增/修改，链接点得开 | "改了若干文件" |
| 失败处理 | 3 次指数退避 → `q.novel.dead`，以 `commentId` 幂等 | "做了重试和幂等" |

#### 补充规则

- **不涉及的子项不许省略，也不许编造**，写 `不涉及：<原因>`。纯文档、纯配置这类没有可交付功能的改动，写 `功能：无（本次只调整汇报模板，无可交付功能）`，不许留空。
- **功能的粒度是"用户能做的一件事"**（注册、登出、导入小说），不是"一个类"或"一层"（写 Mapper、加索引不算功能）。超过 6 个就合并同类项，别把一次任务写成 12 条流水账。
- 只写**实际存在**的东西。设计好但未实现的一律标注"（设计，未实现）"。
- 自检判据：**把汇报发给一个不熟悉本项目的人，他能否在 30 秒内说出"这次为什么这么改"**。说不出来就是名词堆砌，重写。

### 【改动文件】

**一行一个文件，链接必须能点开**，格式固定：

```text
- 新增 — [文件名](相对仓库根的路径) — 一句话说这个文件里改了什么
- 修改 — [文件名](相对仓库根的路径) — 一句话说改了什么
```

- 只列**本次实际动过的文件**，测试、配置、SQL、文档、脚本都算；没动的不列。纯问答、纯排查这类没有落盘改动的任务写 `无（本次未改动文件）`，这一节不许省略。
- 主干文件逐条列；同目录下性质相同的小文件（实体、Mapper、DTO、出入参对象）可以合并成一条，注明数量，并挂上该组里第一个文件的链接。
- 按模块分组（`novel-api` / `novel-core` / `novel-infra` / 文档与脚本），组内按重要性排，不按字母排。
- **只写相对路径**（如 `novel-core/src/main/java/...`）。`/home/...`、`C:\...`、`file://` 一律不合格——那些链接在别人机器上点不开。
- 文件尚未创建、只是设计方案时，标"（设计，未实现）"，**不许编一条点不开的链接**。

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

#### 正例一：同一件事的白话版（认证接入，M1，6 个功能）

> **【实现路径】**
>
> - **整体思路**：认证最容易出的问题不是功能不对，而是技术细节漏进业务层：业务代码里一旦出现"怎么签令牌""用什么哈希密码""缓存用哪个客户端"，以后换实现就得改业务逻辑，也没法脱离中间件做单测。所以只做一件事——把发令牌、记令牌编号、验密码、限登录频率这四个能力定义成业务层的接口，具体技术全推到基础设施层，业务代码只认接口。最大的取舍在"续期"那段：没有走纯无状态路线。本次落地 6 个功能：注册、登录、续期、登出、我的资料、他人主页。
> - **逐功能实现思路**：
>   - **注册** — 先查重名再插入，但真正拦住重名的是数据库唯一索引：两个请求同时通过查询时，后到的那个会被唯一键挡下，捕获后翻译成"用户名已存在"。密码在这一步就落成哈希，库里任何一处都不存明文。
>   - **登录** — 三道关，顺序有讲究：先按 IP 记一次尝试（拦爆破），再验密码，最后看是否封禁。用户不存在时也故意跑一次哈希再报错——否则"查无此人"会明显更快，攻击者拿响应时间就能枚举账号；两种失败对外是同一句话。
>   - **续期** — 令牌里带一个唯一编号，续期时既验签也查白名单。**这里放弃了纯无状态**：纯签名令牌签出去就作废不了，用户点"退出登录"其实退不掉、被盗也止不了损。代价是每次续期多查一次缓存，换来的是能真正踢人。换新时立刻作废旧编号，所以续期令牌是一次性的：被盗用后真正的用户下次续期会失败，异常能被发现。
>   - **登出** — 删掉白名单里的编号即可，刻意做成幂等：令牌过期、伪造、甚至不属于自己都返回成功。否则这个接口就成了"探测令牌是否有效"的工具。
>   - **我的资料** — 读和改都走登录态，昵称、头像键、简介、性别、生日落在资料表；头像只存对象键不存完整地址，将来换 CDN 域名不用刷库。
>   - **他人主页** — 免登录可看，返回公开资料加一个统计块；邮箱、手机号这类字段根本不在返回对象里，靠"不组装"而不是"过滤掉"来避免泄露。
> - **关键组件**：
>   - `TokenPort`（发/验令牌的接口）— 业务层只声明意图，看不见算法名，换实现不动业务代码
>   - `JwtTokenAdapter`（其实现）— 密钥不足 32 字节直接启动失败，带弱密钥跑起来比起不来更危险
>   - `RefreshTokenStore`（续期白名单）— 键 `auth:refresh:{jti}`、值用户 id，TTL 与令牌同寿
>   - `BCryptPasswordHasher` — strength=10，单次 50~100ms，够挡离线爆破又不拖垮登录
>   - `LoginAttemptLimiter` — 键 `auth:login:ip:{ip}`，60 秒 5 次，Lua 保证计数与过期原子
>   - 另有 3 个实体、3 个 Mapper 与 6 个出入参对象，照 V1 表结构直译
> - **失败处理**：用户看到的永远是同一份 `{code,message,traceId}` JSON，不会因为错在过滤器还是错在 Controller 就变成 HTML 错误页——过滤器链上的 401/403 由 `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` 直接写响应，Controller 内被 `@PreAuthorize` 拒绝的走 `GlobalExceptionHandler`。令牌过期、伪造、类型不符统一转 `10105`，不让过期异常冒成 500；注册并发重名转 `10101`；登录超限转 `42900`；续期旧令牌重放转 `10105`。
>
> **【改动文件】**
> - 新增 — [TokenPort.java](novel-core/src/main/java/com/novel/core/user/port/TokenPort.java) — 令牌端口；同目录另有 4 个（仓储、白名单、哈希、限流）
> - 新增 — [AuthService.java](novel-core/src/main/java/com/novel/core/user/application/AuthService.java) — 注册/登录/续期/登出编排；同目录另有 UserService 与 7 个出入参
> - 新增 — [JwtTokenAdapter.java](novel-infra/src/main/java/com/novel/infra/security/JwtTokenAdapter.java) — JJWT 实现；同目录另有 5 个安全适配器与配置类
> - 新增 — [UserRepositoryImpl.java](novel-infra/src/main/java/com/novel/infra/persistence/UserRepositoryImpl.java) — 用户仓储；同目录另有 3 个实体与 3 个 Mapper
> - 新增 — [SecurityConfig.java](novel-api/src/main/java/com/novel/api/config/SecurityConfig.java) — 过滤器链装配与放行规则
> - 新增 — [JwtAuthenticationFilter.java](novel-api/src/main/java/com/novel/api/security/JwtAuthenticationFilter.java) — 解析 Bearer；同目录另有 4 个响应与工具类
> - 新增 — [AuthController.java](novel-api/src/main/java/com/novel/api/controller/AuthController.java)、[UserController.java](novel-api/src/main/java/com/novel/api/controller/UserController.java) — 7 个接口；同目录 4 个请求对象
> - 修改 — [GlobalExceptionHandler.java](novel-api/src/main/java/com/novel/api/web/GlobalExceptionHandler.java) — 补 401/403 分支
> - 修改 — [application.yml](novel-api/src/main/resources/application.yml) — 新增 JWT 与登录限流配置项
> - 新增 — [AuthFlowIntegrationTest.java](novel-api/src/test/java/com/novel/api/AuthFlowIntegrationTest.java) — 13 个端到端用例；另有 3 个单测类
>
> **【验证方式】**
> - 人话级：不带令牌请求 `GET /api/v1/users/me`，应看到 401 且响应体是 `{"code":40100,…}`；带上刚登录拿到的令牌再请求，应看到 200 和自己的昵称。
> - `mvn test` → 39 个用例全绿；其中 `AuthFlowIntegrationTest` 用 Testcontainers 起 MySQL + Redis 跑完整链路，断言"旧 refresh 重放返回 `10105`"。
> - Redis 侧 `docker exec novel-redis redis-cli --scan --pattern 'auth:*'` 应看到 `auth:refresh:{jti}` 与 `auth:login:ip:127.0.0.1`。

#### 正例二：小改动的密度（MQ 接入，2 个功能）

> **【实现路径】**
> - **整体思路**：评论写入和搜索索引必须解耦，否则搜索一抖动就拖垮发评论。但"落库后直接发消息"会丢——事务还没提交，broker 就可能是断的。所以先在同一个事务里写一条待投递记录，再由后台任务投出去：事务保证"库里既然有评论，事件就不会丢"。本次落地 2 个功能：可靠投递、索引同步。
> - **逐功能实现思路**：
>   - **可靠投递** — 写评论时同事务插一条 `outbox` 记录（`message_key` 唯一），事件不进事务外的任何地方；后台任务每轮扫 `status=0` 的行，用 `RabbitTemplate.convertAndSend()` 异步发出并开启投递确认，只有 broker 回了 ack 才算成功，否则 `retry_count+1` 并把 `next_retry_at` 往后推——宁可晚投，也不要"以为投了其实没投"。
>   - **索引同步** — 消费者监听 `q.novel.search.sync`，先写索引再手动 ack。用手动确认而不是自动确认，是为了让"索引真的写完"成为消费成功的标准，否则进程中途挂掉，这条文档就永久丢了。
> - **关键组件**：`novel.event`（Topic，业务事件入口）绑 `q.novel.search.sync`（索引同步队列，键 `novel.search.#`）；`novel.dlx`（Direct）把坏消息收进 `q.novel.dead`（键 `dead`）。前缀是为了在控制台一眼认出本项目的资源。
> - **失败处理**：消费异常先按 3 次指数退避（1s/2s/4s）重试，仍失败则靠 `default-requeue-rejected: false` 送进 `q.novel.dead` 留现场，由补偿任务重放，而不是在客户端无限 requeue 把 CPU 打满；消费端先插 `mq_consume_log(message_key, consumer)`，唯一键冲突即跳过，以此幂等。
>
> **【改动文件】**
> - 新增（设计，未实现）— `OutboxRelay`（轮询投递）与 `CommentConsumer`（索引同步）两个类，文件尚未创建，按规矩不给点不开的链接
> - 修改 — [RabbitConfig.java](novel-infra/src/main/java/com/novel/infra/config/RabbitConfig.java) — 拓扑沿用现有的，只补消费端重试与死信参数
>
> **【验证方式】**
> - 人话级：发一条评论后打开 `http://127.0.0.1:15672` 的队列页，`q.novel.search.sync` 应出现一条入队随即归零，`q.novel.dead` 应为 0。
> - 日志：该 `traceId` 下先出现 `outbox 已写入`，再出现 `search sync consumed`。
> - `mvn test -Dtest=OutboxRelayTest` 断言确认失败的消息 `retry_count` 递增且不会被重复投递。

### 汇报即提交（强制）

**每个任务结束时，在给出汇报的同时执行一次 Git 提交，提交信息就是这份汇报正文。**

1. **暂存**：只 `git add` 本次任务实际改动的路径，**显式列文件名**。禁止 `git add -A` / `git add .`——那会把无关的半成品和本地配置一起卷进提交；`git status --short` 复核无误后再提交。
2. **提交信息 = 汇报正文，但首行是纯结论句**：首行只写【任务结论】的**那句话本身**，**不带** `【任务结论】` 标题；空一行后接 `【实现路径】`、`【改动文件】`、`【验证方式】` 三段（标题和内容照抄汇报）。这样 `git log --oneline` 一眼看到的就是结论，而不是 `【任务结论】 完成了…` 这种带标题的截断行。不加 `docs:` / `feat:` 之类前缀，不写"本次提交…"的额外流水账；结论句受本文件【任务结论】的 40 字上限约束，保证首行不会被 Git 截断。多行信息用 `git commit -F -` + heredoc 传入，不要用多个 `-m` 拼接：

   ```bash
   git add AGENTS.md novel-api/src/main/java/...
   git commit -F - <<'MSG'
   一句话说明完成了什么。

   【实现路径】
   - 整体思路：…
   - 逐功能实现思路：…

   【改动文件】
   - 新增 — [X.java](novel-core/src/main/java/…/X.java) — 做了什么

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

小说阅读 + 社区平台：**模块化单体（Modular Monolith）后端 + React 阅读前端**。后端单进程部署，模块间靠 Maven 依赖方向与 ArchUnit 约束隔离，为将来拆分留口子；前端在 `web/`，已接真实接口（P1）。

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
| 文档 / 监控 | SpringDoc OpenAPI 3 + Actuator/Micrometer | Swagger 即接口演示界面 |
| 前端 | React 19 + TypeScript + Vite 8 + Tailwind 4 + TanStack Query 5 | `web/`，不引组件库；已接真实 API |

### 2.3 模块与依赖方向

```
novel/                      聚合 POM（pom.xml）
├── novel-common/           统一响应 Result<T>、错误码、分页、BizException   （最底层，不依赖任何业务模块）
├── novel-core/             领域模型 + 应用服务 + 端口接口（不引 web/mybatis/redis/es）
├── novel-infra/            MyBatis、Redis、MQ、ES、MinIO、健康探针的适配器
├── novel-api/              Controller、全局异常、traceId、配置、启动类（装配根）
└── web/                    React 前端（Vite + Tailwind；已接真实接口）
```

`web/` 不参与 Maven 构建与 ArchUnit 约束；它通过 HTTP 契约与后端耦合，接口类型手抄在
`web/src/api/types.ts`，字段以 Java record 的 JSON 输出为准。

依赖方向：`novel-api → novel-core ← novel-infra`，`novel-common` 被三者依赖。

**这条方向由 `novel-api/src/test/java/com/novel/api/ArchitectureTest.java` 强制**，共 4 条规则，违规即构建失败：

1. `com.novel.core..` 不得依赖 `com.novel.infra..` / `com.novel.api..`；
2. `com.novel.infra..` 不得依赖 `com.novel.api..`；
3. `com.novel.common..` 不得依赖 core/infra/api；
4. `@RestController` 只能出现在 `com.novel.api..`。

> 新增中间件能力时的正确姿势：core 里定 `port` 接口 → infra 里写 adapter → api 里装配。**不许**为了少写一个转换类让 core 直接 import Mapper。

### 2.4 包结构

现状（M0 骨架 + M1 认证 + M2 阅读 + M3 评分书评 + M4 搜索 + M5 榜单 + M6 社区）：

```
com.novel.common
├── exception/BizException
└── result/{Result, ResultCode, PageQuery, PageResult}

com.novel.core
├── storage/{StoragePort, FileService, FileBizType, PresignedUpload}
├── support/{LikeCounter, ProfileLoader, Texts}
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
├── event/{NovelEventPublisher, NovelChangeType}
├── search/
│   ├── domain/{NovelDoc, SearchHit, Highlight, SuggestItem}
│   ├── port/{NovelSearchPort, SearchUnavailableException}
│   └── application/{SearchService, SearchQuery, SearchSort, SearchTime, view/*}
├── reading/
│   ├── domain/BookshelfItem
│   ├── port/BookshelfRepository
│   └── application/{BookshelfService, view/BookshelfView}
├── review/
│   ├── domain/{Review, ReviewReply, LikeTargetType}
│   ├── port/{RatingRepository, ReviewRepository, LikeRepository}
│   └── application/{RatingService, ReviewService, ReviewSort, view/*}
├── community/
│   ├── domain/{Post, PostRef, PostComment}
│   ├── port/PostRepository
│   └── application/{PostService, PostSort, PostQuery, view/*}
└── rank/
    ├── domain/{RankPeriod, RankEntry, RankAction, ScoredNovel}
    ├── port/{RankScoreStore, RankSnapshotRepository, RankCache}
    └── application/{RankService, RankRefreshService, HotScoreCalculator,
                     RankSettings, RankQuery, view/RankItemView}

com.novel.infra
├── config/{MybatisPlusConfig, RedisConfig, RabbitConfig, StorageConfig, SecurityInfraConfig,
│            RankConfig, RankProperties}
├── persistence/{UserRepositoryImpl, NovelRepositoryImpl, ChapterRepositoryImpl,
│                BookshelfRepositoryImpl, RatingRepositoryImpl, ReviewRepositoryImpl,
│                LikeRepositoryImpl, PostRepositoryImpl, RankSnapshotRepositoryImpl,
│                entity/*, mapper/*}
├── security/{JwtTokenAdapter, JwtProperties, BCryptPasswordHasher,
│             RedisTokenStore, RedisLoginAttemptLimiter, AuthRateLimitProperties}
├── cache/{RedisNovelCache, RedisNovelViewCounter, RedisRankScoreStore, RedisRankCache}
├── event/{OutboxEventPublisher, OutboxRelay, OutboxMessageSender,
│           RabbitOutboxMessageSender, OutboxMessage, OutboxEntity, OutboxProperties}
├── search/{EsNovelSearchAdapter, NovelIndexDefinition, NovelIndexAdmin,
│            NovelIndexBootstrap, NovelIndexRebuildJob, NovelDocAssembler,
│            SearchProperties, SearchInfraConfig}
├── mq/{NovelSearchSyncConsumer, NovelSearchSyncHandler, NovelSearchSyncMessage,
│       ConsumeLogRepository, ConsumeLogEntity}
├── job/{ViewCountFlushJob, RatingRecalcJob, RankRefreshJob}
├── storage/{S3StorageAdapter, StorageProperties}
└── health/DependencyHealthChecker

com.novel.api
├── NovelApplication                        # @SpringBootApplication(scanBasePackages = "com.novel")，排除 UserDetailsServiceAutoConfiguration
├── config/{OpenApiConfig, StorageBootstrap, SecurityConfig, SchedulingConfig}
├── controller/{AuthController, UserController, NovelController, CategoryController,
│               BookshelfController, RatingController, ReviewController, PostController,
│               SearchController, RankController, FileController, SystemController, dto/*}
├── importing/{NovelImportRunner, NovelReindexRunner, RankRefreshRunner}   # @Profile("import" / "reindex" / "rank")，跑完 System.exit
│               demo/{DemoNovelLibrary, DemoNovelWriter, DemoProse, DemoNovel}  # 纯 JDK 演示数据生成器，不依赖 Spring
├── security/{JwtAuthenticationFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler,
│             SecurityResponses, LoginUser, SecurityUtils}
└── web/{TraceIdFilter, ResponseTraceAdvice, GlobalExceptionHandler}
```

**认证的端口倒置**：`TokenPort` / `RefreshTokenStore` / `PasswordHasher` / `LoginAttemptLimiter` 四个接口定义在 core，JJWT、Redis、BCrypt 的实现全部在 infra。core 里没有任何一行代码知道"JWT"这个词。

**搜索的端口倒置**：`NovelSearchPort`（查/建议/写/删）与 `NovelEventPublisher`（"这本书变了"）定义在 core，
ES 的 DSL、索引名、别名切换、Outbox 表全在 infra。core 里没有一行知道"Elasticsearch"这个词，
`SearchUnavailableException` 是唯一的技术失败出口，由应用服务翻译成业务码 `10601`。

**榜单的端口倒置**：`RankScoreStore`（记一次行为）、`RankSnapshotRepository`（读写快照）、`RankCache`（读缓存）
定义在 core，ZSET 的 key、Lua 脚本、`novel_rank` 的 SQL 全在 infra。core 里没有一行知道"Redis"或"ZSET"这个词。

> 榜单**故意不走 MQ**。理由写在 `RankScoreStore` 的注释里：它是近似指标，丢一次行为不影响正确性；
> 而阅读是最频繁的写，每读一次就往 outbox 写一行，等于用"不丢事件"的代价换一个本来就允许有误差的数字。
> 于是 `q.novel.rank.agg` 到 M6 结束仍然只有拓扑：M5 的榜单不用它，M6 的社区热帖也选了读时 SQL 排序。

**社区的端口倒置**：`PostRepository`（帖子/引用/评论）定义在 core，MyBatis-Plus 实体、分页与热帖 SQL 全在 infra。
帖子不写 outbox、不进 ES：社区没有跨进程同步需求，写库同事务改计数就够了。

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

> 现状：**M4 起 `q.novel.search.sync` 已接通**——生产者是 `OutboxRelay`（轮询投递，等 broker 确认），
> 消费者是 `NovelSearchSyncConsumer`（手动 ack，失败 3 次退避后进 `q.novel.dead`）。
> `q.novel.rank.agg` / `q.novel.stat.agg` 仍然只有拓扑。**M5 的榜单没有用 `q.novel.rank.agg`**（见 §2.4 末尾），
> **M6 的社区热帖也没有用**——它按读时 SQL 公式排序，两个队列到 M6 结束仍只有拓扑。
>
> 事件类型即路由键：`novel.search.created` / `novel.search.updated` / `novel.search.deleted`，
> 都落在 `novel.search.#` 下。以后加榜单同步时用 `novel.rank.#` 区分，不必改交换机。

**Redis**（`RedisConfig`）：`StringRedisTemplate` + 自定义 `RedisTemplate<String,Object>`，key 用 `StringRedisSerializer`，value 用 `GenericJackson2JsonRedisSerializer`（JSON 里带 `@class`）。禁止 JDK 序列化——`redis-cli` 要能直接看懂缓存内容。

已占用的键空间：

| 键 | 类型 | TTL | 用途 |
|---|---|---|---|
| `auth:refresh:{jti}` | String → userId | 与 refresh 令牌同寿（7d） | refresh 白名单，登出/轮换即删 |
| `auth:login:ip:{ip}` | String 计数 | 60s | 登录限流，Lua 保证 INCR+EXPIRE 原子 |
| `novel:chapter:text:{chapterId}` | String | 2h | 章节正文缓存；`chapter_content` 是 MEDIUMTEXT，命中最省事 |
| `novel:categories` | String(JSON) | 10m | 分类树，读多写零；导入完成时主动失效 |
| `novel:view:delta` | Hash(field=novelId) | 无 | 阅读数增量，`ViewCountFlushJob` 每 60s 取出并 `HINCRBY` 回库 |
| `rank:novel:{period}:{bucket}` | ZSET(member=novelId) | 日 3d / 周 4w / 月 100d | 榜单实时层，行为发生时 `ZINCRBY`；总榜不在这里（读库里的累计列） |
| `rank:seen:{yyyyMMdd}` | Hash | 3d | 阅读去重，field = `{novelId}` + 竖线 + `u:{userId}` 或 `ip:{addr}`；一个日 Hash 而不是每人一个 key |
| `rank:cache:{period}:{bucket}` | String(JSON) | 5m | 榜单读缓存，结算替换快照后主动删；key 带日期所以跨天自动失配 |

> 小说详情**不缓存**：主键查询 1ms，为它维护失效逻辑是负优化。缓存只留收益明确的两处（正文、分类）与一个写放大点（阅读数）。

> 本机 6379 的主机 Redis 时常不在线，`deploy/docker-compose.yml` 里提供了 `redis` 兜底服务（映射 **6380**，避开主机端口），需要时 `docker compose up -d redis`，并在 `application-local.yml` 里写 `spring.data.redis.port: 6380`。

**对象存储**（`StorageConfig` → `S3StorageAdapter`）：AWS SDK 走 S3 协议，`pathStyleAccessEnabled(true)`（MinIO 必需），`S3Client` 与 `S3Presigner` 的 endpoint/region/凭证必须完全一致。`StorageBootstrap`（`@Order(100)` 的 `ApplicationRunner`）启动时幂等建 bucket 并设公共读策略，失败只打 warn 不影响启动。

**MySQL / Flyway**：迁移脚本在 `novel-api/src/main/resources/db/migration`，`V1__init_schema.sql` + `V2__seed_categories.sql` + `V3__add_novel_version.sql` + `V4__backfill_novel_review_count.sql`，共 21 张业务表 + `flyway_schema_history`。MyBatis-Plus 拦截器顺序固定为 **分页 → 乐观锁 → 防全表攻击**，分页 `maxLimit=50`。

**ES**（`SearchProperties` → `EsNovelSearchAdapter`）：

| 资源 | 名称 | 说明 |
|---|---|---|
| 别名 | `novel` | 读写都走它，重建时原子切换 |
| 物理索引 | `novel_v1`、`novel_v2`… | 字段映射建好不能改，换分词器只能重建 |
| 重建 | 每天 02:00（`novel.search.rebuild-cron`） | 写 `v{N+1}` → 刷新 → 切别名 → 删旧索引（保留 `keep-indexes` 份）；**任一篇写入失败就整体放弃，不切别名** |
| 手动重建 | `--spring.profiles.active=reindex` | 运维动作，不暴露成 HTTP 接口 |

- 文档字段：`title` / `authorName` / `intro`（text，IK）、`titleSuggest`（completion）、`categoryId` / `status` / `wordCount` / `avgRating` / `viewCount` / `hotScore` / `latestUpdateAt`。
- 写入带**外部版本号**（`novel.version`）：旧消息写进来会被集群以 409 拒绝，这是乱序消费的兜底。
- 查询用 `multi_match`（`title^3, authorName^2, intro`）而不是 `query_string`，避免用户输入被当成查询语法。
- 深分页窗口 `page*size ≤ 1000`，超过回 400；不提供游标翻页（接口要返回 total 与页码）。
- IK 插件烤在 `deploy/es/Dockerfile` 里（官方镜像不带）。没有插件的机器把 `novel.search.analyzer` 配成 `standard`，不自动降级。
- 映射是 `dynamic: strict`：给 `NovelDoc` 加字段却忘了同步映射时，写入会直接失败并进死信，而不是让 ES 猜一个类型。
- 重建的失败语义：写入/补写阶段任何失败都丢弃新索引、**不切别名**（旧索引继续服务）；切别名超时要先确认别名指向，已在服务的新索引不许删。
- 索引初始化用 `SmartLifecycle`（`phase = Integer.MIN_VALUE`），必须早于 Rabbit 监听器：别名缺失时任何写入都会让 ES 自动建出**同名索引**，之后别名再也建不起来。真遇到了删掉同名索引重启即可。
- `viewCount` 是**快照**：只在导入、评分、对账、重建时写入，阅读数按分钟落库不触发同步，所以"最热"排序与结果里的阅读数最多滞后一天，M5 接入真实 `hotScore` 后由它接管。

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

> `novel.version`（V3 新增）是搜索索引的同步版本号，内容每变更一次 +1。它**不是**乐观锁：
> 实体上刻意没有 `@Version`，两种语义混在一起后"版本冲突该重试还是该丢弃"就说不清了。

> `user_stat` 的四个计数（`bookshelf_count` / `review_count` / `post_count` / `like_received`）**当前由业务代码同步维护**
> —— 书架增删、书评增删、发帖删帖、点赞增减各自加减一次，不依赖 MQ。将来接 `q.novel.stat.agg` 改成事件驱动时，
> 必须把这几处直接写库的调用一并去掉，否则会双写。
>
> `novel` 上的 `favorite_count` / `review_count` 同理：前者随书架增删、后者随书评增删同步维护。
> 这两列是榜单的算分输入（总榜的原始行为量 = 阅读×1 + 收藏×3 + 书评×2），
> 少维护一个，总榜就会和日/周/月榜用两套刻度——`review_count` 曾经就是这样漏了整整四个里程碑。

### 2.8 响应与错误码契约

- 统一响应 `Result<T>{code, message, data, traceId, success}`，`code=0` 成功；`success` 由 `isSuccess()` 序列化而来，与 `code==0` 等价。
- 分层策略：**业务失败 → HTTP 200 + 业务码**；参数/协议错误 → 4xx；未捕获异常 → 500 且必须打完整堆栈。
- 错误码分段：`0` 成功；`4xxxx` 客户端（与 HTTP 对齐）；`5xxxx` 服务端；`1xxxx` 业务按域（1-1 用户 / 1-2 小说 / 1-4 评分评论 / 1-5 社区 / 1-6 搜索）。
- 分页入参 `page/size`，`size` 硬上限 50（`PageQuery.MAX_SIZE` 与分页插件双保险）；出参 `PageResult{list,total,page,size,hasNext}`。
- 时间统一 `Asia/Shanghai`，列类型 `DATETIME`（不用 `TIMESTAMP`）。
- 密码只存 BCrypt 哈希（strength=10，`$2a$10$` 前缀）。
- **401/403 有两套出口，但必须是同一份 JSON**：过滤器链上的由 `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` 直接写响应（此时 `@RestControllerAdvice` 还没介入）；Controller 内 `@PreAuthorize` 拒绝的由 `GlobalExceptionHandler` 处理。两者都输出 `Result{code,message,traceId}`，401 对应 `40100`，403 对应 `40300`。
- 登录失败一律返回 `10103 用户名或密码错误`，不区分"用户不存在"与"密码错误"。
- **区分两种"错"**：请求合法但业务不允许（重名、无权限、已赞过）→ `BizException` → 200 + 业务码；
  取值本身不合法（`sort=nonsense`、评分 0 星）→ `IllegalArgumentException` → 400。
  判据是"同样的请求重试一次会不会变好"：不会变好的是协议错误，该 4xx。

### 2.9 配置与环境变量

`application.yml` 可提交；密码只进 `application-local.yml`（已被 `.gitignore` 忽略，仓库只留 `.example`），通过 `spring.config.import: optional:classpath:application-local.yml` 加载。

常用环境变量：`NOVEL_DB_HOST/PORT/NAME/USER/PASSWORD`、`NOVEL_REDIS_HOST/PORT/PASSWORD/DB`、`NOVEL_MQ_HOST/PORT/USER/PASSWORD`、`NOVEL_ES_URI`、`NOVEL_MINIO_ENDPOINT/PUBLIC_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET`、`NOVEL_JWT_SECRET`、`NOVEL_SERVER_PORT`。

`novel.jwt.secret` 是**唯一没有默认值**的配置项：为空或不足 32 字节时 `JwtProperties.validate()` 直接抛 `IllegalStateException`，应用起不来。这是刻意的——带着弱密钥跑起来比起不来危险得多。生成命令：`openssl rand -base64 48`。

其他配置项：`novel.jwt.access-ttl`(30m) / `refresh-ttl`(7d) / `issuer`(novel)、`novel.auth.rate-limit.login-max-attempts`(5) / `login-window`(60s)、`novel.reading.view-flush-interval-ms`(60000)、`novel.import.dir`(deploy/samples)、`novel.review.rating-recalc-cron`(0 30 3 * * ?)。

搜索与投递：`novel.search.enabled`(true) / `alias`(novel) / `index-prefix`(novel) / `analyzer`(ik_max_word) /
`search-analyzer`(ik_smart) / `bulk-size`(500) / `rebuild-cron`(0 0 2 * * ?) /
`retry-delays-ms`(1000,2000,4000) / `keep-indexes`(2)；`novel.outbox.enabled`(true) / `poll-interval-ms`(1000) /
`batch-size`(100) / `max-attempts`(6) / `confirm-timeout-ms`(3000)。

> `spring.task.scheduling.pool.size` 设为 **5**：默认只有 1 个线程，凌晨重建会把阅读数落库、Outbox 投递、
> 榜单结算一起卡住。现在有 5 个 `@Scheduled` 任务，池子必须至少一样大。

> 榜单：`novel.rank.enabled`(true) / `refresh-cron`(`0 */10 * * * ?`) / `snapshot-size`(200) / `cache-ttl`(5m) /
> `view-weight`(1) / `favorite-weight`(3) / `review-weight`(2) / `decay-exponent`(0.5) / `decay-offset-days`(2)。
> 权重只有这一处：实时层加权和总榜算分都从这里读，改一处两边一起变。

> `import` profile 关掉 `novel.search.enabled` 与 `novel.outbox.enabled`：导入进程只写库，
> 不建索引也不投递（短命进程等不到 broker 确认就退出了）。留库里的待投递行由常驻服务补发。

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

# 手动重建搜索索引（不起 Web；写新索引 → 刷新 → 切别名 → 删旧索引）
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=reindex

# 手动结算榜单（不起 Web；日/周/月/总四个周期各算一遍后退出）
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=rank

# 生成 28 本演示书（纯 JDK，不连库）→ 再用上面的 import 命令导 deploy/samples/demo
java -cp novel-api/target/classes com.novel.api.importing.demo.DemoNovelWriter deploy/samples/demo

# 前端（web/）
cd web && pnpm install
cd web && pnpm dev                        # http://127.0.0.1:5173，/api 代理到 8080
cd web && pnpm typecheck && pnpm build
cd web && pnpm smoke                      # 18 条真实后端断言；需后端 + 演示数据 + dev 在跑

# 测试
mvn test                                  # 187 个用例，含 Testcontainers（需 Docker）
mvn test -Dtest=ArchitectureTest          # 只跑架构约束
mvn test -Dtest=SchemaMigrationTest       # 只跑 Flyway 迁移
mvn test -Dtest=AuthFlowIntegrationTest   # 认证链路端到端
mvn test -Dtest=AuthServiceTest           # 认证业务逻辑单元测试
mvn test -Dtest=ReadingFlowIntegrationTest # 导入→列表→正文→书架端到端
mvn test -Dtest=TxtNovelParserTest        # TXT 解析器纯单测
mvn test -Dtest=ReviewFlowIntegrationTest  # 评分/书评/点赞端到端
mvn test -Dtest=PostFlowIntegrationTest    # 社区发帖/引用/点赞/评论端到端
mvn test -Dtest=PostServiceTest            # 社区计数回退与详情自增边界单测
mvn test -Dtest=RatingServiceTest          # 评分增量聚合单测
mvn test -Dtest=SearchFlowIntegrationTest  # 导入→投递→可搜、评分同步、重建切别名
mvn test -Dtest=OutboxRelayTest            # 投递退避与"放弃"状态流转
mvn test -Dtest=NovelSearchSyncHandlerTest # 消费幂等/重试/毒消息
mvn test -Dtest=SearchQueryTest            # 搜索入参边界
mvn test -Dtest=RankPeriodTest             # 榜单周期归属，含 ISO 周跨年
mvn test -Dtest=HotScoreCalculatorTest     # 热度公式边界（负计数、时钟回拨、除零）
mvn test -Dtest=RankFlowIntegrationTest    # 行为→结算→读榜端到端
mvn test -Dtest=DemoNovelWriterTest,DemoNovelImportTest \
  -Dsurefire.failIfNoSpecifiedTests=false   # 演示数据生成与导入链路（不跑全量）
```

自检入口：`/swagger-ui.html`、`/api/v1/system/ping`、`/api/v1/system/dependencies`（五项中间件实测）、`/actuator/health`。

### 2.11 里程碑

- [x] **M0 骨架**：四模块工程、Flyway 21 表、统一响应、全局异常、traceId、Swagger、中间件体检、预签名上传闭环
- [x] **M1 用户与认证**：注册/登录/刷新/登出、JWT 双令牌 + Redis 白名单、Spring Security 过滤器链、401/403 统一响应、登录限流、个人资料读写、他人主页免登录
- [x] **M2 阅读**：TXT 解析与批量导入（幂等）、小说列表/详情、目录分页、正文 + Redis 缓存、阅读数写缓冲、书架（收藏 + 进度 + 有新章标记）
- [x] **M3 评分书评**：1-5 星一人一票（增量聚合 + 每日对账自愈）、书级评论区（最热/最新、软删除、越权拦截）、楼中楼两层、点赞幂等
- [x] **M4 搜索**：ES 索引 + 别名 + IK、Outbox 增量同步（投递确认 / 手动 ack / 死信）、手写 DSL 查询（高亮 / 筛选 / 五种排序）、搜索建议、每日全量重建（零停机切别名、写入失败不切换）
- [x] **M5 榜单**：日/周/月/总四榜（ISO 周对齐）、Redis ZSET 实时层 + 按用户/IP 按天去重、10 分钟结算快照（整份替换）、读缓存、`hot_score` 回写（不进索引，等每日重建）
- [x] **M6 社区**：帖子（结构化引用小说卡片 + 占位符回显）、最新/最热列表、帖子点赞、两层评论与评论点赞、作者软删除、发帖/评论/点赞计数同步维护
- [x] **F1 前端原型**：设计文档 + 9 个页面 + 程序化封面 + Playwright 冒烟
- [x] **F2 演示数据**：28 本虚构书（8 类）确定性生成器 + `deploy/samples/demo` + 导入链路定向测试
- [x] **F3 前后端联调**：9 个页面接真实 API（TanStack Query、令牌轮换、单飞刷新、错误态/空态/骨架屏），`web/src/mocks` 删除，18 条真实后端冒烟
- [ ] M7 工程收尾（压测 / CI / 架构图）

### 2.12 前端（web/，已接真实接口）

技术栈见 2.2；目录与运行方式见 `web/README.md`，设计决策见 `docs/03-前端设计.md`。

- **状态**：9 个页面全部调用真实接口，`web/src/mocks` 已删除；演示账号 `bookworm / novel123456` 是后端真实账号。
- **请求层**：`src/api/client.ts` 负责 `Result<T>` 解包、`ApiError`、access 内存 + refresh localStorage、单飞刷新与 401 重放一次；`endpoints.ts` 集中所有路径；`queries.ts` 是 TanStack Query hooks。
- **数据**：分类由 `CategoryProvider` 统一提供；页面不手拼 URL，不各自缓存。详情查询 `staleTime=5min`、全站 `refetchOnWindowFocus:false`——详情接口每次调用阅读数 +1。
- **样式**：Tailwind 4 的 `@theme` 是唯一 token 源；封面无素材时按「书名 + 作者」确定性生成，有 `coverKey` 时用 MinIO 真图。
- **接口类型**：手抄在 `web/src/api/types.ts`，字段以 Java record 的 JSON 输出为准；可空字段会被后端省略，类型写成可选。
- **联调**：Vite 把 `/api` 代理到 `127.0.0.1:8080`，后端不需要 CORS；生产由 Nginx 同域转发。
- **验证**：`pnpm typecheck`、`pnpm build`、`pnpm smoke`（18 条断言，需后端 + 演示数据 + dev 在跑）。

---

## 三、改动时的硬约束

1. **不许破坏依赖方向**：三方源码都要过 `ArchitectureTest`；新中间件能力必须走 core 的 `port` 接口。
2. **SQL 迁移脚本必须 LF**：`.gitattributes` 已锁 `eol=lf`，行尾变化会让 Flyway checksum 不匹配。已执行的迁移脚本**禁止修改**，只能新增 `V{n}__xxx.sql`。
3. **禁止 JDK 序列化**：Redis 用 JSON，MQ 用 `Jackson2JsonMessageConverter`。
4. **禁止在事务里直接发 MQ**：写库与事件写 `outbox` 同事务，由投递任务异步发出；消费者以 `mq_consume_log` 唯一键做幂等。
5. **密码/密钥不进 `application.yml`**：走 `application-local.yml` 或环境变量。
6. **`size` 上限 50**：新分页接口必须用 `PageQuery`，不得放开限制。
7. **不要主动拉新的中间件 SDK**：MinIO 走 S3 协议（AWS SDK），ES 客户端版本以 Boot BOM 为准。
8. **搜索索引写入必须带版本号**：`novel.version` 是唯一的顺序依据，缺了它，迟到的旧消息会把新内容盖回去且没有任何报错。
9. **新增功能先写测试再写实现**：每个红绿循环的红灯必须是"测试跑起来并失败"（先建空壳让编译通过），
   不能是编译不过；每完成一个模块只跑该模块的相关测试，全量 `mvn test` 留到功能收尾。
10. **索引只当副本**：任何"读自己刚写的数据"都走 MySQL；ES 挂了搜索返回 `10601`，其余接口不受影响。
11. **前端颜色与字体只从 token 取**：`web/src/styles/index.css` 的 `@theme` 是唯一来源，页面里不写死十六进制。
12. **高亮 HTML 只保留 `<em>`**：引擎返回的片段在插入页面前剥掉其余标签与属性；用户输入永远不能当 HTML 渲染。
13. **详情接口有副作用**：`GET /novels/{id}` 每次调用都会让阅读数 +1，前端禁止 hover 预取、
    `refetchOnWindowFocus` 与无意义的重复请求。
