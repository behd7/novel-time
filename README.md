# Novel · 小说平台后端

小说阅读 + 社区平台，模块化单体（Modular Monolith）。

**设计文档**：[docs/01-设计方案.md](docs/01-设计方案.md)

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 语言 / 构建 | Java 17 + Maven 多模块 | Spring Boot 3.3.5 |
| 主库 | MySQL 8.0 | 正文也在库里，不放对象存储 |
| 缓存 / 榜单 / 计数 | Redis 8.6.1 | Lettuce + ZSET + 计数 |
| 消息 | RabbitMQ 4.x | Outbox 模式，配死信队列 |
| 搜索 | Elasticsearch 8.13 | IK 分词、BM25、高亮；MySQL 才是真相 |
| 对象存储 | MinIO（S3 协议） | 封面/头像/导入原文件 |
| 持久层 | MyBatis-Plus 3.5.7 + Flyway | SQL 可控，schema 版本化 |
| 文档 | SpringDoc OpenAPI 3 | `/swagger-ui.html` 就是演示界面 |

## 模块划分

```
novel
├── novel-common   统一响应 / 错误码 / 分页 / 业务异常     （最底层，谁都不依赖它）
├── novel-core     领域模型 + 应用服务 + 端口接口          （不引 web/mybatis/redis）
├── novel-infra    MyBatis、Redis、MQ、ES、MinIO 适配器
└── novel-api      Controller、全局异常、配置、启动类      （装配根）
```

依赖方向 `api → core ← infra`，由 [ArchitectureTest](novel-api/src/test/java/com/novel/api/ArchitectureTest.java) 强制校验，违规即构建失败。

## 本地环境

| 组件 | 位置 | 地址 | 凭证 |
|---|---|---|---|
| MySQL 8.0.42 | Windows 主机 | `127.0.0.1:3306` | 需通过 `NOVEL_DB_USER/NOVEL_DB_PASSWORD` 传入 |
| Redis 8.6.1 | Windows 主机（`6379`）或 Docker 兜底（`6380`） | `127.0.0.1` | 无密码 |
| RabbitMQ 4.3.2 | Windows 主机 | `127.0.0.1:5672`，控制台 `:15672` | `guest / guest` |
| Elasticsearch 8.13.0 | Docker（本仓库 compose） | `127.0.0.1:9200` | 无鉴权（仅开发） |
| MinIO | Docker（本仓库 compose） | `127.0.0.1:9000`，控制台 `:9001` | `novel / novel12345` |

> WSL 使用 mirrored 网络模式，因此 `127.0.0.1` 可以直接访问 Windows 主机上的中间件。

### 启动中间件

```bash
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml ps
```

> 主机上的 Redis 若没启动（`6379` 无监听），用 compose 里的兜底实例：`docker compose up -d redis`，
> 它会映射到 **6380**（避开主机端口），然后在 `application-local.yml` 写 `spring.data.redis.port: 6380`。

ES 是从 `deploy/es/Dockerfile` 构建的（在官方 8.13.0 上装 IK 分词插件）。首次用或换版本时先构建：

```bash
docker compose -f deploy/docker-compose.yml build elasticsearch
docker compose -f deploy/docker-compose.yml up -d elasticsearch
```

> 没装/装不上 IK 时，把 `novel.search.analyzer` 与 `search-analyzer` 配成 `standard` 也能跑，
> 只是中文会按单字切分。这里不做自动降级——悄悄换分词器比报错更难排查。

### 建库

```sql
CREATE DATABASE novel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

表结构由 Flyway 在应用启动时自动迁移（`novel-api/src/main/resources/db/migration`）。

### 启动应用

密码不要写进 `application.yml`（它是要提交到仓库的），放到被忽略的覆盖层里：

```bash
cp novel-api/src/main/resources/application-local.yml.example \
   novel-api/src/main/resources/application-local.yml
# 编辑 application-local.yml：
#   spring.datasource.password   MySQL 密码
#   novel.jwt.secret             openssl rand -base64 48 的输出（必填，否则启动即失败）
mvn -pl novel-api -am spring-boot:run
```

也可以完全用环境变量，不建文件：

```bash
export NOVEL_DB_USER=root NOVEL_DB_PASSWORD='你的密码'
export NOVEL_JWT_SECRET="$(openssl rand -base64 48)"
mvn -pl novel-api -am spring-boot:run
```

或打包后运行：

```bash
mvn -DskipTests package
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar
```

### 自检

| 地址 | 用途 |
|---|---|
| http://127.0.0.1:8080/swagger-ui.html | 接口文档 |
| http://127.0.0.1:8080/api/v1/system/ping | 存活探针 |
| http://127.0.0.1:8080/api/v1/system/dependencies | **中间件体检**（MySQL/Redis/MQ/ES/MinIO + 搜索索引状态） |
| http://127.0.0.1:8080/actuator/health | Actuator 健康检查 |

## 认证

无状态 JWT 双令牌。`access` 30 分钟、`refresh` 7 天，refresh 的 `jti` 存 Redis 白名单，支持登出与轮换吊销。

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 否 | 注册，返回双令牌（201） |
| POST | `/api/v1/auth/login` | 否 | 登录，同 IP 每分钟 5 次 |
| POST | `/api/v1/auth/refresh` | 否 | 刷新，旧 refresh 立即作废 |
| POST | `/api/v1/auth/logout` | 是 | 吊销 refresh，幂等 |
| GET | `/api/v1/users/me` | 是 | 当前用户 |
| PUT | `/api/v1/users/me` | 是 | 改资料，只改传了的字段 |
| GET | `/api/v1/users/{id}` | 否 | 他人主页（含统计） |

```bash
# 注册 → 拿令牌 → 访问受保护接口
curl -X POST http://127.0.0.1:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"bookworm","password":"novel123456","nickname":"书虫"}'

curl http://127.0.0.1:8080/api/v1/users/me -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 测试

```bash
mvn test                                    # 183 个用例（含 Testcontainers，需要 Docker）
mvn test -Dtest=ArchitectureTest            # 只跑架构约束
mvn test -Dtest=AuthFlowIntegrationTest     # 认证链路端到端（起 MySQL + Redis 容器）
mvn test -Dtest=AuthServiceTest             # 认证业务逻辑单元测试（纯 Mock，1 秒内跑完）
mvn test -Dtest=ReadingFlowIntegrationTest  # 导入→列表→正文→书架端到端
mvn test -Dtest=TxtNovelParserTest          # TXT 解析器纯单测
mvn test -Dtest=ReviewFlowIntegrationTest   # 评分/书评/点赞端到端
mvn test -Dtest=RatingServiceTest           # 评分增量聚合单测
mvn test -Dtest=PostFlowIntegrationTest     # 社区发帖/引用/点赞/评论端到端
```

## 阅读

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/v1/novels` | 否 | 列表，`categoryId` / `status` / `sort=latest\|newest\|wordCount\|hot` |
| GET | `/api/v1/novels/{id}` | 否 | 详情，含末章信息；每次访问累加一次阅读数 |
| GET | `/api/v1/novels/{id}/chapters` | 否 | 目录，按 `seq` 升序分页 |
| GET | `/api/v1/novels/{id}/chapters/{seq}` | 否 | 正文，带 `prevSeq` / `nextSeq` |
| GET | `/api/v1/categories` | 否 | 两级分类（扁平返回，前端拼树） |
| GET | `/api/v1/bookshelf` | 是 | 我的书架，按最近阅读倒序，带 `hasUpdate` |
| POST | `/api/v1/bookshelf` | 是 | 加入书架（幂等） |
| DELETE | `/api/v1/bookshelf/{novelId}` | 是 | 移出书架（物理删除，可再次加入） |
| PUT | `/api/v1/bookshelf/{novelId}/progress` | 是 | 更新进度 `{"seq": 3}` |

## 评分与书评

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/v1/novels/{id}/rating` | 否 | 平均分、人数、我的评分、星级分布 |
| PUT | `/api/v1/novels/{id}/rating` | 是 | 打分或改分（改分不增加人数） |
| DELETE | `/api/v1/novels/{id}/rating` | 是 | 取消评分，幂等 |
| GET | `/api/v1/novels/{id}/reviews` | 否 | 书评列表，`sort=hot\|latest` |
| POST | `/api/v1/novels/{id}/reviews` | 是 | 发书评，带 `score` 会同时记入评分 |
| DELETE | `/api/v1/reviews/{id}` | 是 | 只删自己的（软删除） |
| GET | `/api/v1/reviews/{id}/replies` | 否 | 楼中楼，带 `parentNickname` |
| POST | `/api/v1/reviews/{id}/replies` | 是 | 回复，带 `parentReplyId` 即楼中楼 |
| POST/DELETE | `/api/v1/reviews/{id}/like` | 是 | 点赞/取消，幂等 |
| POST/DELETE | `/api/v1/replies/{id}/like` | 是 | 回复点赞/取消 |

评分用"明细 + 冗余聚合"：`novel_rating` 存每人一票，`novel` 上冗余 `rating_sum/rating_count/avg_rating`，
打分时只做一次加减，从不 `AVG()` 全表扫。每天凌晨 `RatingRecalcJob` 从明细重算一遍，把漂移的聚合值拉回来。

### 导入小说

仓库自带三本样书（`deploy/samples/*.txt`），格式是「可选头部 + `---` + 按 `第N章` 切分」：

```bash
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=import --novel.import.dir=deploy/samples
```

导完自动退出；**同名书会跳过**，所以命令可以放心重跑。

### 缓存与计数

- 章节正文缓存 2 小时（`novel:chapter:text:{id}`），分类缓存 10 分钟（`novel:categories`）
- 小说详情**不缓存**：主键查询 1ms，为它维护失效逻辑不划算
- 阅读数先累加到 `novel:view:delta`（Redis Hash），由 `ViewCountFlushJob` 每 60 秒合并回库

## 社区

帖子里的引用是**结构化**的：正文只留 `[[ref:0]]`，真实关系落 `post_novel_ref`，详情接口把
`refs` 连同书名/作者/封面/评分一起返回，前端按 `refText` 替换成卡片。小说改名换封面自动同步，也伪造不了外链。

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/v1/posts` | 否 | 列表，`sort=hot\|latest`，可选 `novelId` 反查引用了这本书的帖子 |
| POST | `/api/v1/posts` | 是 | 发帖，`refNovelIds` 的下标对应正文里的 `[[ref:N]]`，最多 5 本 |
| GET | `/api/v1/posts/{id}` | 否 | 详情，返回正文 + `refs` 卡片；浏览数 +1 |
| DELETE | `/api/v1/posts/{id}` | 是 | 只删自己的（软删除） |
| POST/DELETE | `/api/v1/posts/{id}/like` | 是 | 点赞/取消，幂等 |
| GET | `/api/v1/posts/{id}/comments` | 否 | 平铺分页，前端按 `rootId` 归并成两层 |
| POST | `/api/v1/posts/{id}/comments` | 是 | 发评论，带 `parentCommentId` 即楼中楼 |
| DELETE | `/api/v1/comments/{id}` | 是 | 只删自己的 |
| POST/DELETE | `/api/v1/comments/{id}/like` | 是 | 评论点赞/取消，幂等 |

- 帖子与评论都走软删除（`deleted=1`），查询自动过滤；楼中楼可能还引用着主楼，不能物理删
- 点赞复用书评那张 `like_record`，唯一键 `(target_type, target_id, user_id)` 保证一人一次
- 热帖按 `(like_count*3 + comment_count*5) / POW(小时数+2, 1.2)` 读时排序，和书评最热同一写法
- 计数同步维护：`post.like_count` / `comment_count`、`post_comment.like_count`、`user_stat.post_count` / `like_received`
- 社区不写 outbox、不进 ES、不新增 Redis 键

## 搜索

MySQL 是唯一真相，ES 只是可重建的索引副本。写路径只有一个出口：业务事务里落一条 `outbox`，
后台任务投递并等 broker 确认，消费端回库读最新数据再整篇覆盖索引。

```
导入/评分 ─(同事务)→ outbox ─(轮询投递, 等确认)→ novel.event → q.novel.search.sync
                                                                    │ 回查 MySQL
用户搜索 → SearchController → SearchPort → ES 索引 ←(整篇覆盖, 带版本号)┘
```

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/v1/search?q=青云` | 否 | 书名/作者/简介搜索，返回高亮片段；`categoryId`、`status`、`sort`、分页 |
| GET | `/api/v1/search/suggest?q=青` | 否 | 书名前缀补全，最多 10 条 |

- 排序：`relevance`（默认）/ `latest` / `hot` / `rating` / `wordCount`
- 深分页窗口 `page*size ≤ 1000`，超过返回 400：搜索翻到第 20 页以后，代价随页码线性上涨
- ES 不可用时搜索返回 `10601`，列表/详情/书架不受影响
- 索引映射是 `dynamic: strict`：文档出现未声明字段会直接写失败，逼着 `NovelDoc` 和映射同步改
- 阅读数与热度分都是**快照**：只在导入、评分、对账、重建时写进索引；榜单每 10 分钟回写的 `hot_score` 不发同步事件，"最热"排序最多滞后到次日重建

手动重建索引（写 `novel_v2` → 刷新 → 原子切别名 → 删旧索引，期间搜索不断服）：

```bash
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=reindex
```

## 榜单

四张榜共用一套公式：`raw = 阅读×1 + 收藏×3 + 书评×2`，`score = log10(1 + raw)`；
总榜再叠一层时间衰减，防止五年前完结的老书永远压在首页。

```
详情页/收藏/书评 ─(就地 ZINCRBY)→ rank:novel:{period}:{bucket}   ← 日 / 周 / 月
                                          │ 每 10 分钟结算
                                          ├→ novel_rank 快照 + 清读缓存
库里的累计列 ────(全表扫描)───────────────┘→ novel.hot_score（等每日重建进索引）
```

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/v1/ranks?period=daily` | 否 | 日/周/月/总榜；返回名次、热度分与小说卡片 |

- `period` 取 `daily` / `weekly` / `monthly` / `all`；周按 ISO 8601（周一为第一天），跨年周不会串桶
- 阅读按「同一个人同一天同一本书」去重，匿名按 IP：一次 Lua 完成判重 + 三桶累加
- 收藏与书评**双向记账**（取消时 -3 / -2），否则「收藏→取消→再收藏」就是免费刷分
- 结算每 10 分钟一轮，`novel_rank` 每次整份替换（先删后插），表不会随时间膨胀
- 总榜不读 ZSET：`rank:novel:all` 只能从上线那天开始累加，M2 导入的书在它眼里是 0 分
- `hot_score` 回写**不自增 `version`**：它不是内容变更，索引等每日 02:00 重建刷新
- 榜单读的是快照，最多滞后一个结算周期；Redis 挂了榜单降级为空，阅读与搜索不受影响

手动结算一次（不起 Web，跑完退出）：

```bash
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=rank
```

## 进度

- [x] **M0 骨架**：多模块工程、Flyway 迁移、统一响应、全局异常、traceId、Swagger、中间件体检
- [x] **M1 用户与认证**：注册/登录/刷新/登出、JWT 双令牌 + Redis 白名单、Spring Security、登录限流、个人资料、他人主页
- [x] **M2 阅读**：TXT 解析与幂等导入、小说列表/详情、目录、正文 + 缓存、阅读数写缓冲、书架
- [x] **M3 评分书评**：1-5 星一人一票、平均分增量维护 + 每日对账、书级评论区（最热/最新）、楼中楼、点赞幂等
- [x] **M4 搜索**：ES 索引 + 别名 + IK、Outbox 增量同步（投递确认 / 手动 ack / 死信）、高亮与筛选排序、搜索建议、每日全量重建
- [x] **M5 榜单**：日/周/月/总四榜、Redis ZSET 实时层（按人按天去重）、10 分钟结算快照、读缓存、`hot_score` 回写
- [x] **M6 社区**：帖子 + 结构化小说引用（卡片回显、按书反查）、最新/最热列表、帖子与评论点赞、两层评论、作者软删除、计数同步维护
- [ ] M7 工程收尾（压测 / CI / 架构图）

## 约定

- 响应统一 `{code, message, data, traceId, success}`，`code=0` 成功
- 业务失败 → HTTP 200 + 业务码；参数/协议错误 → 4xx；未捕获异常 → 500
- 401/403 由 Security 过滤器直接写响应，与 `Result` 同构（`40100` / `40300`）
- 分页 `page/size`，`size` 上限 50
- 所有时间 `Asia/Shanghai`，数据库 `DATETIME`
- 密码只存 BCrypt 哈希；登录失败不区分"用户不存在"与"密码错误"
