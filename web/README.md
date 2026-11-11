# 夜读 · Web

React 19 + TypeScript + Vite + Tailwind CSS 4 的小说阅读前端。**已接入真实后端接口（P1）**：
15 个页面读 MySQL / ES 里的数据，登录走真实 JWT，`web/src/mocks` 已删除。

设计说明见 [docs/03-前端设计.md](../docs/03-前端设计.md)，后端接口见
[docs/01-设计方案.md](../docs/01-设计方案.md)。

![首页](../docs/assets/frontend/home.jpg)

## 技术栈

| 层 | 选型 |
|---|---|
| 框架 | React 19 + TypeScript 5.9 |
| 构建 | Vite 8（`@` 别名、`/api` 开发代理） |
| 样式 | Tailwind CSS 4（`@theme` token，无组件库） |
| 路由 | React Router 7 |
| 服务端状态 | TanStack Query 5（缓存、去重、失效、骨架屏） |
| 字体 | Noto Serif SC / Noto Sans SC / Inter（`@fontsource-variable` 自托管，unicode-range 切片） |
| 图标 | lucide-react |
| 冒烟测试 | Playwright（`scripts/smoke.mjs`，34 条断言） |

## 快速开始

先起后端与中间件，并把演示数据导进库（详见仓库根 README 的「导入小说」）：

```bash
# 1. 后端（另一个终端；需要 MySQL / Redis / RabbitMQ / ES / MinIO）
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar

# 2. 演示数据（首次或重置后执行；同名书会跳过）
java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=import --novel.import.dir=deploy/samples/demo
```

再起前端：

```bash
cd web
pnpm install
pnpm seed:demo   # 首次或重置库后执行，确保 bookworm 存在
pnpm dev
# → http://127.0.0.1:5173
```

开发期 `/api` 会代理到 `http://127.0.0.1:8080`，浏览器看到的是同源请求，因此后端不需要开 CORS。
需要改目标地址时复制 `.env.example` 为 `.env` 或直接设置环境变量：

```bash
NOVEL_API_TARGET=http://127.0.0.1:8080 pnpm dev
```

生产构建由 Nginx 同域转发 `/api` 与 `/`，`VITE_API_BASE` 默认就是 `/api/v1`。

## 演示账号

```
bookworm / novel123456
```

这是**后端真实账号**，登录页的「一键填入」只是帮你填表；提交后走
`POST /api/v1/auth/login`，拿到 JWT 双令牌。密码只存在后端 BCrypt 哈希里。
新库或重置库后用 `pnpm seed:demo` 创建（幂等：已存在则登录一次确认密码）。

## 页面

| 路由 | 页面 | 数据来源 |
|---|---|---|
| `/` | 首页 | `GET /novels`（热门/最近）、`GET /ranks`、`GET /categories`、`GET /bookshelf` |
| `/browse` | 书库 | `GET /novels?categoryId&status&sort&page&size` |
| `/search` | 搜索 | `GET /search`、`GET /search/suggest` |
| `/rank` | 榜单 | `GET /ranks?period&page&size` |
| `/novel/:id` | 详情 | `GET /novels/{id}`、`/chapters`、`/reviews`、`/rating`、`/bookshelf` |
| `/novel/:id/chapters` | 目录 | `GET /novels/{id}/chapters?page&size` |
| `/novel/:id/chapters/:seq` | 阅读器 | `GET /novels/{id}/chapters/{seq}` |
| `/login` | 登录/注册 | `POST /auth/login`、`POST /auth/register`、`POST /auth/refresh` |
| `/bookshelf` | 我的书架 | `GET /bookshelf`、`DELETE /bookshelf/{id}` |
| `/me` | 个人主页 | `GET /users/{id}`、`PUT /users/me`、`GET /bookshelf` |
| `/community` | 社区 | `GET /posts`、帖子点赞；发帖入口 |
| `/posts/new` | 写帖子 | `POST /posts`、`GET /novels` |
| `/posts/:id` | 帖子详情 | `GET /posts/{id}`、评论/回复/点赞/删除 |
| `/users/:id` | 他人主页 | `GET /users/{id}` |
| `/styleguide` | 设计系统 | 静态样例（不调接口） |

## 数据流

```
pages
  → src/api/queries.ts        useQuery / useMutation 包装，统一 queryKey 与 staleTime
  → src/api/endpoints.ts      所有后端路径、分页参数、写操作集中在这里
  → src/api/client.ts         fetch → Result<T> 解包 → ApiError；401 单飞刷新后重放一次
```

- **access 令牌只放内存**，刷新页面即失效；**refresh 令牌放 localStorage**，启动时换一对新令牌。
- `POST /auth/refresh` 是一次性轮换：并发 401 由 `refreshSession()` 单飞，避免后 9 个请求拿旧 jti 互相作废。
- 写操作不自动重试；只有 401/10105 会在刷新后重放一次。
- `GET /novels/{id}` 每次调用阅读数 +1，所以详情查询 `staleTime=5min` 且全站关掉 `refetchOnWindowFocus`。

## 目录

```
src/
├── api/
│   ├── client.ts       # fetch、Result 解包、令牌与单飞刷新
│   ├── endpoints.ts    # 所有接口函数
│   ├── queries.ts      # TanStack Query hooks 与 queryKey
│   └── types.ts        # 与 Java View 一一对应
├── components/
│   ├── book/           # NovelCover / NovelCard / NovelRow / ContinueCard
│   ├── layout/         # SiteLayout / SiteHeader / SiteFooter
│   ├── search/         # SearchBox（真实建议、键盘导航）
│   └── ui/             # Button / Stars / Tag / Segmented / Pagination / LoadError / ...
├── features/auth/      # 真实登录、刷新、登出
├── features/categories/# 分类字典与树（GET /categories）
├── lib/                # cn / format / cover / progress / useDebouncedValue
├── pages/              # 15 个页面
└── styles/index.css    # @theme token、字体、阅读器主题、纸张颗粒
```

## 设计约定

- 颜色、字体只从 `src/styles/index.css` 的 `@theme` 取；页面里不写死十六进制。
- 程序化封面：书名 + 作者做 FNV-1a 哈希，选 10 组配色之一与三种版式之一；有 `coverKey` 时用 MinIO 真图。
- 高亮 HTML 只保留 `<em>`，其余标签与属性剥掉再插入。
- 分类是扁平数组，由 `CategoryProvider` 拼树并提供 `nameOf / topOf / childrenOf`；封面与卡片不再各自查 mock。
- 阅读器设置与本地进度存 `localStorage`；登录且书在书架里时，额外把进度回写 `PUT /bookshelf/{id}/progress`。
- 当前演示数据都挂在**顶级分类**下，所以书库/搜索只展示顶级分类；子类只作为题材说明，不做筛选。

## 验证

```bash
pnpm typecheck   # tsc -b
pnpm build       # 产物 web/dist

# 交互冒烟（需要后端 + 演示数据 + 另一个终端在跑 pnpm dev）
pnpm exec playwright install chromium   # 首次
pnpm smoke
```

`pnpm smoke` 覆盖：未登录书架提示、真实登录、刷新后恢复会话、首页/书库真实数据、搜索建议与结果数、
搜索空态、详情目录、评分落库、加入书架、阅读器字号持久化、键盘翻章、本地进度、服务端书架进度、
书架筛选与移出、个人主页统计/正在读/资料编辑、社区发帖/评论/楼中楼回复/删帖、书评写入与回复、
他人主页、退出登录、控制台零错误，共 **34 条断言**。脚本结束时通过 API 清理冒烟帖子与书评，可反复跑。

Playwright 版本与已装浏览器不一致时，用 `CHROME_PATH` 指定本机 Chromium：

```bash
CHROME_PATH=/path/to/chrome pnpm smoke
```

## 现状与下一步

- **现在（P1 完成）**：15 个页面全部接真实接口；请求层、令牌轮换、分页、筛选、错误态、骨架屏均已接入。
- **书架/个人主页（已完成）**：书架可筛选、继续阅读、两段式移出；个人主页显示统计/正在读/书架预览，可编辑昵称与简介。
- **社区/书评（已完成）**：帖子列表/详情/发帖、两层评论与点赞、删除自己的内容；书评写入/回复/点赞/删除；他人主页。
- **下一步（P2）**：路由级代码分割、图片懒加载、首屏字体 preload、Lighthouse 与无障碍实测。
- **暂未做**：我的书评/帖子列表（后端无按用户列内容的接口）、头像/帖子封面上传、社区治理。
- **数据现状**：库内 30 本，评分/书评很少，详情页会正常显示空态；这不是前端 bug。

## 已知取舍

- SPA 没有 SSR/SEO；作品集阶段接受，真要 SEO 应换渲染方式而不是堆 meta。
- 不引组件库，分页/Toast/浮层都是手写；多人协作时这个取舍应反过来。
- 封面是程序化生成，不是真实素材；它的定位是「没有素材时也不难看」的兜底。
- refresh 放 localStorage 是为了刷新后免登录；生产环境应换 `httpOnly + SameSite=Lax` Cookie，需要后端配合。
