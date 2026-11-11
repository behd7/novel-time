/**
 * 与后端视图对象一一对应的类型。
 *
 * 字段名按 Java record 的 JSON 输出写，不改成前端更顺手的形状：
 * 多一层映射就多一处会漂移的地方，出问题时也说不清是前端拼错了还是后端没给。
 *
 * 两个必须记住的差异：
 * 1. 后端用 `@JsonInclude(NON_NULL)`，null 字段会被**省略**而不是返回 null，
 *    所以 `coverKey`、`prevSeq` 这类可空字段一律写成可选；
 * 2. 时间序列化成 "2026-11-05T21:30:00"，这里按 string 存，展示层再格式化。
 */

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
  success: boolean;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
}

/** 只够渲染一张封面的信息：列表、榜单、书架、继续阅读共用。 */
export interface BookCoverInfo {
  title: string;
  authorName: string;
  coverKey?: string | null;
  categoryId: number;
}

export interface NovelSummary extends BookCoverInfo {
  id: number;
  /** 0 连载中 / 1 已完结 */
  status: number;
  intro: string;
  wordCount: number;
  chapterCount: number;
  avgRating: number;
  ratingCount: number;
  viewCount: number;
  favoriteCount: number;
  latestUpdateAt: string;
}

export interface NovelDetail extends NovelSummary {
  latestChapterSeq?: number | null;
  latestChapterTitle?: string | null;
}

export interface Category {
  id: number;
  name: string;
  /** 顶级分类为 0 */
  parentId: number;
  sortNo: number;
}

export interface ChapterSummary {
  seq: number;
  title: string;
  wordCount: number;
  free: boolean;
}

export interface ChapterContent {
  novelId: number;
  chapterId: number;
  seq: number;
  title: string;
  content: string;
  wordCount: number;
  /** 第一章 / 最后一章时后端会省略该字段 */
  prevSeq?: number | null;
  nextSeq?: number | null;
}

export interface SearchHighlight {
  title?: string | null;
  authorName?: string | null;
  intro?: string | null;
}

export interface SearchHit extends BookCoverInfo {
  novelId: number;
  categoryName: string;
  status: number;
  intro: string;
  wordCount: number;
  chapterCount: number;
  avgRating: number;
  ratingCount: number;
  viewCount: number;
  latestUpdateAt: string;
  highlight?: SearchHighlight | null;
}

export interface SuggestItem {
  novelId: number;
  title: string;
  authorName: string;
}

export interface RankItem {
  rankNo: number;
  /** 热度分，只在同一周期内可比 */
  score: number;
  novel: NovelSummary;
}

export interface RatingSummary {
  avgRating: number;
  ratingCount: number;
  myScore?: number | null;
  /** 键 1..5，没有的星级不出现 */
  distribution: Record<string, number>;
}

export interface Review {
  id: number;
  userId: number;
  nickname: string;
  avatarKey?: string | null;
  content: string;
  score?: number | null;
  likeCount: number;
  replyCount: number;
  liked: boolean;
  createdAt: string;
}

export interface ReviewReply {
  id: number;
  reviewId: number;
  userId: number;
  nickname: string;
  avatarKey?: string | null;
  content: string;
  likeCount: number;
  liked: boolean;
  createdAt: string;
}

export interface User {
  id: number;
  username: string;
  nickname: string;
  avatarKey?: string | null;
  bio?: string | null;
  gender?: number | null;
  birthday?: string | null;
  createdAt: string;
}

export interface AuthResult {
  accessToken: string;
  refreshToken: string;
  /** access 令牌剩余有效秒数 */
  expiresIn: number;
  user: User;
}

export interface BookshelfItem extends BookCoverInfo {
  novelId: number;
  totalChapters: number;
  lastChapterSeq: number;
  hasUpdate: boolean;
  lastReadAt: string;
  addedAt: string;
}
