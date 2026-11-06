# 夜读 · Web

React 19 + TypeScript + Vite + Tailwind CSS 4 的小说阅读前端。
当前是**高保真原型**：9 个页面、完整交互与状态，数据来自 `src/mocks`，尚未接入后端接口。

设计说明见 [docs/03-前端设计.md](../docs/03-前端设计.md)。

![首页](../docs/assets/frontend/home.jpg)

## 技术栈

| 层 | 选型 |
|---|---|
| 框架 | React 19 + TypeScript 5.9 |
| 构建 | Vite 8（`@` 别名、`/api` 开发代理） |
| 样式 | Tailwind CSS 4（`@theme` token，无组件库） |
| 路由 | React Router 7 |
| 字体 | Noto Serif SC / Noto Sans SC / Inter（`@fontsource-variable` 自托管，unicode-range 切片） |
| 图标 | lucide-react |
| 冒烟测试 | Playwright（`scripts/smoke.mjs`） |

## 快速开始

```bash
cd web
pnpm install
pnpm dev
# → http://127.0.0.1:5173
```

开发期 `/api` 会代理到 `http://127.0.0.1:8080`，浏览器看到的是同源请求，因此后端不需要开 CORS。
需要改目标地址时复制 `.env.example` 为 `.env` 或直接设置环境变量：

```bash
NOVEL_API_TARGET=http://127.0.0.1:8080 pnpm dev
```

## 演示账号

原型阶段是前端模拟登录，只认这一个账号：

```
bookworm / novel123456
```

登录页有「一键填入」。数据存在 `localStorage` 的 `novel.demo.user`，退出登录即清空。

## 页面

| 路由 | 页面 | 说明 |
|---|---|---|
| `/` | 首页 | Hero、继续阅读、本周热读、分类、编辑推荐、最近更新 |
| `/browse` | 书库 | 分类树、状态/排序、网格/列表、分页 |
| `/search` | 搜索 | 高亮命中、分类/状态/排序、建议下拉、空态 |
| `/rank` | 榜单 | 日/周/月/总四榜，前三名重点展示 |
| `/novel/:id` | 详情 | 评分、加入书架、简介、目录预览、书评、相似推荐 |
| `/novel/:id/chapters` | 目录 | 每页 30 章、跳章、读到标记 |
| `/novel/:id/chapters/:seq` | 阅读器 | 字号/行距/主题、键盘翻章、进度记忆 |
| `/login` | 登录/注册 | 与后端一致的校验、业务码错误提示 |
| `/styleguide` | 设计系统 | token、组件、封面版式、状态与阅读主题 |

## 目录

```
src/
├── api/types.ts        # 与 Java View 一一对应
├── components/
│   ├── book/           # NovelCover / NovelCard / NovelRow / ContinueCard
│   ├── layout/         # SiteLayout / SiteHeader / SiteFooter
│   ├── search/         # SearchBox（建议、键盘导航）
│   └── ui/             # Button / Stars / Tag / Segmented / Pagination / ...
├── features/auth/      # 登录状态（原型为 mock）
├── lib/                # cn / format / cover / progress
├── mocks/              # 14 本书、26 条书评、4 个榜单周期、章节正文
├── pages/              # 9 个页面
└── styles/index.css    # @theme token、字体、阅读器主题、纸张颗粒
```

## 设计约定

- 颜色、字体只从 `src/styles/index.css` 的 `@theme` 取；页面里不写死十六进制。
- 程序化封面：书名 + 作者做 FNV-1a 哈希，选 10 组配色之一与三种版式之一；有 `coverKey` 时用 MinIO 真图。
- 高亮 HTML 只保留 `<em>`，其余标签与属性剥掉再插入。
- 阅读器设置与进度都存 `localStorage`；隐私模式下写入失败不打断阅读。

## 验证

```bash
pnpm typecheck   # tsc -b
pnpm build       # 产物 web/dist

# 交互冒烟（需要另一个终端在跑 pnpm dev）
pnpm exec playwright install chromium   # 首次
pnpm smoke
```

`pnpm smoke` 覆盖：登录、评分、书架切换、阅读器字号持久化、键盘翻章、进度写回、搜索建议、搜索结果、空态、
控制台零错误，共 15 条断言。CI 里没有图形环境时可传 `CHROME_PATH` 指定本机 Chromium。

## 现状与下一步

- **现在**：页面与交互完整，数据全部来自 `mocks`；后端未启动也能演示。
- **P1**：接入真实接口与请求层（access 内存 + refresh 轮换 + 单飞刷新），引入 TanStack Query；
  页面从 `mocks` 切到 hooks，`src/mocks` 只保留给 Storybook/截图使用。
- **不要**对 `GET /api/v1/novels/{id}` 做 hover 预取或窗口聚焦重取：它每次调用都会让阅读数 +1。

## 已知取舍

- SPA 没有 SSR/SEO；作品集阶段接受，真要 SEO 应换渲染方式而不是堆 meta。
- 不引组件库，分页/Toast/浮层都是手写；多人协作时这个取舍应反过来。
- 封面是程序化生成，不是真实素材；它的定位是「没有素材时也不难看」的兜底。
