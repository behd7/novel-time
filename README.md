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
| Redis 8.6.1 | Windows 主机 | `127.0.0.1:6379` | 无密码 |
| RabbitMQ 4.3.2 | Windows 主机 | `127.0.0.1:5672`，控制台 `:15672` | `guest / guest` |
| Elasticsearch 8.13.0 | Docker（本仓库 compose） | `127.0.0.1:9200` | 无鉴权（仅开发） |
| MinIO | Docker（本仓库 compose） | `127.0.0.1:9000`，控制台 `:9001` | `novel / novel12345` |

> WSL 使用 mirrored 网络模式，因此 `127.0.0.1` 可以直接访问 Windows 主机上的中间件。

### 启动中间件

```bash
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml ps
```

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
# 编辑 application-local.yml，填 spring.datasource.password
mvn -pl novel-api -am spring-boot:run
```

也可以完全用环境变量，不建文件：

```bash
export NOVEL_DB_USER=root NOVEL_DB_PASSWORD='你的密码'
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

### 测试

```bash
mvn test                                    # 全部（含 Testcontainers，需要 Docker）
mvn test -Dtest=ArchitectureTest            # 只跑架构约束
```

## 进度

- [x] **M0 骨架**：多模块工程、Flyway 迁移、统一响应、全局异常、traceId、Swagger、中间件体检
- [ ] M1 用户与认证（JWT + Spring Security）
- [ ] M2 小说/章节/导入/阅读/书架
- [ ] M3 评分与书评
- [ ] M4 搜索（ES + IK + Outbox 同步）
- [ ] M5 榜单
- [ ] M6 社区
- [ ] M7 工程收尾（压测 / CI / 架构图）

## 约定

- 响应统一 `{code, message, data, traceId, success}`，`code=0` 成功
- 业务失败 → HTTP 200 + 业务码；参数/协议错误 → 4xx；未捕获异常 → 500
- 分页 `page/size`，`size` 上限 50
- 所有时间 `Asia/Shanghai`，数据库 `DATETIME`
- 密码只存 BCrypt 哈希
