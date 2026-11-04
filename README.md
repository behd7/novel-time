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
| http://127.0.0.1:8080/api/v1/system/dependencies | **中间件体检**（MySQL/Redis/MQ/ES/MinIO 逐个探测） |
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
mvn test                                    # 77 个用例（含 Testcontainers，需要 Docker）
mvn test -Dtest=ArchitectureTest            # 只跑架构约束
mvn test -Dtest=AuthFlowIntegrationTest     # 认证链路端到端（起 MySQL + Redis 容器）
mvn test -Dtest=AuthServiceTest             # 认证业务逻辑单元测试（纯 Mock，1 秒内跑完）
mvn test -Dtest=ReadingFlowIntegrationTest  # 导入→列表→正文→书架端到端
mvn test -Dtest=TxtNovelParserTest          # TXT 解析器纯单测
mvn test -Dtest=ReviewFlowIntegrationTest   # 评分/书评/点赞端到端
mvn test -Dtest=RatingServiceTest           # 评分增量聚合单测
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

## 进度

- [x] **M0 骨架**：多模块工程、Flyway 迁移、统一响应、全局异常、traceId、Swagger、中间件体检
- [x] **M1 用户与认证**：注册/登录/刷新/登出、JWT 双令牌 + Redis 白名单、Spring Security、登录限流、个人资料、他人主页
- [x] **M2 阅读**：TXT 解析与幂等导入、小说列表/详情、目录、正文 + 缓存、阅读数写缓冲、书架
- [x] **M3 评分书评**：1-5 星一人一票、平均分增量维护 + 每日对账、书级评论区（最热/最新）、楼中楼、点赞幂等
- [ ] M4 搜索（ES + IK + Outbox 同步）
- [ ] M5 榜单
- [ ] M6 社区
- [ ] M7 工程收尾（压测 / CI / 架构图）

## 约定

- 响应统一 `{code, message, data, traceId, success}`，`code=0` 成功
- 业务失败 → HTTP 200 + 业务码；参数/协议错误 → 4xx；未捕获异常 → 500
- 401/403 由 Security 过滤器直接写响应，与 `Result` 同构（`40100` / `40300`）
- 分页 `page/size`，`size` 上限 50
- 所有时间 `Asia/Shanghai`，数据库 `DATETIME`
- 密码只存 BCrypt 哈希；登录失败不区分"用户不存在"与"密码错误"
