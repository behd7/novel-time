import type {
  AuthResult,
  ChapterContent,
  ChapterSummary,
  NovelDetail,
  NovelSummary,
  PageResult,
  RankItem,
  RatingSummary,
  Review,
  SearchHit,
  SuggestItem,
} from '@/api/types';
import { categoryIds, categoryName } from './categories';
import { chapterTitleFor, moodOf, titlesFor } from './chapterTitles';
import { novels, novelById, toSummary } from './novels';
import { featuredChapter, proseFor } from './prose';
import { rankItems, type RankPeriod } from './ranks';
import { distributionOf, reviewsOf } from './reviews';
import { bookshelf, DEMO_CREDENTIALS, demoUser } from './user';

export * from './categories';
export * from './chapterTitles';
export * from './novels';
export * from './ranks';
export * from './reviews';
export * from './user';

/* ------------------------------------------------------------------ */
/* 分页与高亮：与后端 PageResult 的语义保持一致                        */
/* ------------------------------------------------------------------ */

function paginate<T>(items: T[], page: number, size: number): PageResult<T> {
  const safePage = Math.max(1, page);
  const safeSize = Math.min(Math.max(1, size), 50);
  const start = (safePage - 1) * safeSize;
  return {
    list: items.slice(start, start + safeSize),
    total: items.length,
    page: safePage,
    size: safeSize,
    hasNext: safePage * safeSize < items.length,
  };
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * 高亮片段。
 * 先按原文定位、再分段转义，避免用户输入的 <script> 被当成高亮标签塞进页面。
 */
function highlight(text: string, keyword: string): string | null {
  const q = keyword.trim();
  if (!q) return null;
  const index = text.toLowerCase().indexOf(q.toLowerCase());
  if (index < 0) return null;
  const before = escapeHtml(text.slice(0, index));
  const hit = escapeHtml(text.slice(index, index + q.length));
  const after = escapeHtml(text.slice(index + q.length));
  return `${before}<em>${hit}</em>${after}`;
}

/* ------------------------------------------------------------------ */
/* 小说                                                                */
/* ------------------------------------------------------------------ */

export type NovelListSort = 'latest' | 'newest' | 'wordCount' | 'hot';

export interface NovelListParams {
  categoryId?: number;
  status?: number;
  sort?: NovelListSort;
  page?: number;
  size?: number;
}

function matchesCategory(novel: NovelDetail, categoryId?: number): boolean {
  if (!categoryId) return true;
  return categoryIds(categoryId).includes(novel.categoryId);
}

export function listNovels(params: NovelListParams = {}): PageResult<NovelSummary> {
  const { categoryId, status, sort = 'latest', page = 1, size = 20 } = params;
  const filtered = novels.filter(
    (novel) => matchesCategory(novel, categoryId) && (status == null || novel.status === status),
  );

  const sorted = [...filtered].sort((a, b) => {
    switch (sort) {
      case 'newest':
        return b.id - a.id;
      case 'wordCount':
        return b.wordCount - a.wordCount;
      case 'hot':
        return b.viewCount + b.favoriteCount * 3 - (a.viewCount + a.favoriteCount * 3);
      default:
        return b.latestUpdateAt.localeCompare(a.latestUpdateAt);
    }
  });

  return paginate(sorted.map(toSummary), page, size);
}

export function getNovel(id: number): NovelDetail | undefined {
  return novelById(id);
}

/** 同分类优先，不足时用其他高分书补齐；详情页底部的「相似推荐」。 */
export function similarNovels(id: number, limit = 4): NovelSummary[] {
  const current = novelById(id);
  if (!current) return [];
  const sameCategory = novels.filter(
    (novel) => novel.id !== id && novel.categoryId === current.categoryId,
  );
  const others = novels
    .filter((novel) => novel.id !== id && novel.categoryId !== current.categoryId)
    .sort((a, b) => b.avgRating - a.avgRating);
  return [...sameCategory, ...others].slice(0, limit).map(toSummary);
}

/* ------------------------------------------------------------------ */
/* 章节                                                                */
/* ------------------------------------------------------------------ */

function chapterText(novel: NovelDetail, seq: number): string {
  const featured = featuredChapter(novel.id, seq);
  return featured ? featured.paragraphs.join('\n\n') : proseFor(moodOf(novel.categoryId), seq);
}

export function listChapters(novelId: number, page = 1, size = 50): PageResult<ChapterSummary> {
  const novel = novelById(novelId);
  if (!novel) {
    return { list: [], total: 0, page, size, hasNext: false };
  }
  const total = novel.chapterCount;
  const safePage = Math.max(1, page);
  const safeSize = Math.min(Math.max(1, size), 50);
  const start = (safePage - 1) * safeSize;
  const end = Math.min(total, start + safeSize);
  const list: ChapterSummary[] = [];

  for (let seq = start + 1; seq <= end; seq += 1) {
    const content = chapterText(novel, seq);
    list.push({
      seq,
      title: chapterTitleFor(novel.id, novel.categoryId, seq, total, novel.latestChapterTitle),
      wordCount: content.replace(/\s/g, '').length,
      free: true,
    });
  }

  return { list, total, page: safePage, size: safeSize, hasNext: safePage * safeSize < total };
}

export function getChapter(novelId: number, seq: number): ChapterContent | undefined {
  const novel = novelById(novelId);
  if (!novel || seq < 1 || seq > novel.chapterCount) return undefined;
  const content = chapterText(novel, seq);
  return {
    novelId,
    chapterId: novelId * 1000 + seq,
    seq,
    title: chapterTitleFor(novel.id, novel.categoryId, seq, novel.chapterCount, novel.latestChapterTitle),
    content,
    wordCount: content.replace(/\s/g, '').length,
    prevSeq: seq > 1 ? seq - 1 : null,
    nextSeq: seq < novel.chapterCount ? seq + 1 : null,
  };
}

/** 所有章节的标题，目录页做"跳转到第 N 章"时不需要再请求。 */
export function allChapterTitles(novelId: number): string[] {
  const novel = novelById(novelId);
  if (!novel) return [];
  return titlesFor(novel.id, novel.categoryId, novel.chapterCount, novel.latestChapterTitle);
}

/* ------------------------------------------------------------------ */
/* 搜索                                                                */
/* ------------------------------------------------------------------ */

export type SearchSort = 'relevance' | 'latest' | 'hot' | 'rating' | 'wordCount';

export interface SearchParams {
  q: string;
  categoryId?: number;
  status?: number;
  sort?: SearchSort;
  page?: number;
  size?: number;
}

function toHit(novel: NovelDetail, keyword: string): SearchHit {
  const summary = toSummary(novel);
  return {
    novelId: summary.id,
    title: summary.title,
    authorName: summary.authorName,
    coverKey: summary.coverKey,
    categoryId: summary.categoryId,
    status: summary.status,
    intro: summary.intro,
    wordCount: summary.wordCount,
    chapterCount: summary.chapterCount,
    avgRating: summary.avgRating,
    ratingCount: summary.ratingCount,
    viewCount: summary.viewCount,
    latestUpdateAt: summary.latestUpdateAt,
    categoryName: categoryName(summary.categoryId),
    highlight: {
      title: highlight(summary.title, keyword),
      authorName: highlight(summary.authorName, keyword),
      intro: highlight(summary.intro, keyword),
    },
  };
}

export function searchNovels(params: SearchParams): PageResult<SearchHit> {
  const { q, categoryId, status, sort = 'relevance', page = 1, size = 20 } = params;
  const keyword = q.trim();
  if (!keyword) return paginate<SearchHit>([], page, size);

  const lower = keyword.toLowerCase();
  const filtered = novels.filter((novel) => {
    const hit =
      novel.title.toLowerCase().includes(lower) ||
      novel.authorName.toLowerCase().includes(lower) ||
      novel.intro.toLowerCase().includes(lower);
    return hit && matchesCategory(novel, categoryId) && (status == null || novel.status === status);
  });

  const relevance = (novel: NovelDetail): number => {
    const title = novel.title.toLowerCase();
    if (title === lower) return 1000;
    if (title.startsWith(lower)) return 800;
    if (title.includes(lower)) return 600;
    if (novel.authorName.toLowerCase().includes(lower)) return 400;
    return 200;
  };

  const sorted = [...filtered].sort((a, b) => {
    switch (sort) {
      case 'latest':
        return b.latestUpdateAt.localeCompare(a.latestUpdateAt);
      case 'hot':
        return b.viewCount - a.viewCount;
      case 'rating':
        return b.avgRating - a.avgRating;
      case 'wordCount':
        return b.wordCount - a.wordCount;
      default:
        return relevance(b) - relevance(a) || b.ratingCount - a.ratingCount;
    }
  });

  return paginate(sorted.map((novel) => toHit(novel, keyword)), page, size);
}

export function suggest(keyword: string, limit = 8): SuggestItem[] {
  const q = keyword.trim().toLowerCase();
  if (!q) return [];
  return novels
    .filter(
      (novel) =>
        novel.title.toLowerCase().includes(q) || novel.authorName.toLowerCase().includes(q),
    )
    .slice(0, limit)
    .map((novel) => ({ novelId: novel.id, title: novel.title, authorName: novel.authorName }));
}

/* ------------------------------------------------------------------ */
/* 榜单                                                                */
/* ------------------------------------------------------------------ */

export function listRanks(period: RankPeriod, page = 1, size = 20): PageResult<RankItem> {
  return paginate(rankItems(period, 10), page, size);
}

/* ------------------------------------------------------------------ */
/* 评分与书评                                                          */
/* ------------------------------------------------------------------ */

/** 演示账号读过的评分：详情页会显示「你的评分」。 */
const MY_SCORES: Record<number, number> = { 1: 5, 6: 4 };

export function getRatingSummary(novelId: number, userId: number | null = null): RatingSummary {
  const novel = novelById(novelId);
  const distribution = distributionOf(novelId);
  const total = Object.values(distribution).reduce((sum, value) => sum + value, 0);
  return {
    avgRating: novel?.avgRating ?? 0,
    ratingCount: total,
    myScore: userId != null ? (MY_SCORES[novelId] ?? null) : null,
    distribution,
  };
}

export function listReviews(
  novelId: number,
  sort: 'hot' | 'latest' = 'hot',
  page = 1,
  size = 10,
): PageResult<Review> {
  const list = [...reviewsOf(novelId)].sort((a, b) =>
    sort === 'latest'
      ? b.createdAt.localeCompare(a.createdAt)
      : b.likeCount - a.likeCount || b.createdAt.localeCompare(a.createdAt),
  );
  return paginate(list, page, size);
}

/* ------------------------------------------------------------------ */
/* 书架与账号                                                          */
/* ------------------------------------------------------------------ */

export function getBookshelf() {
  return bookshelf;
}

export function findBookshelfItem(novelId: number) {
  return bookshelf.find((item) => item.novelId === novelId);
}

/** 原型阶段的登录：只认演示账号，其余一律走「用户名或密码错误」分支。 */
export async function mockLogin(username: string, password: string): Promise<AuthResult> {
  await new Promise((resolve) => setTimeout(resolve, 520));
  if (username !== DEMO_CREDENTIALS.username || password !== DEMO_CREDENTIALS.password) {
    const error = new Error('用户名或密码错误') as Error & { code: number };
    error.code = 10103;
    throw error;
  }
  return {
    accessToken: 'mock.access.token',
    refreshToken: 'mock.refresh.token',
    expiresIn: 1800,
    user: demoUser,
  };
}

export async function mockRegister(
  username: string,
  _password: string,
  nickname?: string,
): Promise<AuthResult> {
  await new Promise((resolve) => setTimeout(resolve, 620));
  if (username === DEMO_CREDENTIALS.username) {
    const error = new Error('用户名已被占用') as Error & { code: number };
    error.code = 10101;
    throw error;
  }
  return {
    accessToken: 'mock.access.token',
    refreshToken: 'mock.refresh.token',
    expiresIn: 1800,
    user: {
      ...demoUser,
      id: 2,
      username,
      nickname: nickname?.trim() || username,
      bio: null,
      createdAt: new Date().toISOString(),
    },
  };
}
