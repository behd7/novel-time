import { del, get, post, put, query } from './client';
import type {
  AuthResult,
  BookshelfItem,
  Category,
  ChapterContent,
  ChapterSummary,
  NovelDetail,
  NovelSummary,
  PageResult,
  PostComment,
  PostDetail,
  PostView,
  PresignedUpload,
  RankItem,
  RatingSummary,
  Review,
  ReviewReply,
  SearchHit,
  SuggestItem,
  User,
  UserDetail,
  UserReview,
} from './types';

export type NovelListSort = 'latest' | 'newest' | 'wordCount' | 'hot';
export type SearchSort = 'relevance' | 'latest' | 'hot' | 'rating' | 'wordCount';

export interface NovelListParams {
  categoryId?: number;
  status?: number;
  sort?: NovelListSort;
  page?: number;
  size?: number;
}

export interface SearchParams {
  q: string;
  categoryId?: number;
  status?: number;
  sort?: SearchSort;
  page?: number;
  size?: number;
}

export interface PageParams {
  page?: number;
  size?: number;
}

/**
 * 所有后端调用只从这里出。
 *
 * 页面不直接拼 URL：字段名、分页参数、需要登录的写操作集中在一处，
 * 后端改路径时不用全站搜索字符串。
 */
export const api = {
  categories: () => get<Category[]>('/categories'),

  novels: (params: NovelListParams = {}) =>
    get<PageResult<NovelSummary>>(`/novels${query({ ...params })}`),
  novel: (id: number) => get<NovelDetail>(`/novels/${id}`),
  chapters: (novelId: number, params: PageParams = {}) =>
    get<PageResult<ChapterSummary>>(`/novels/${novelId}/chapters${query({ ...params })}`),
  chapter: (novelId: number, seq: number) =>
    get<ChapterContent>(`/novels/${novelId}/chapters/${seq}`),

  search: (params: SearchParams) =>
    get<PageResult<SearchHit>>(`/search${query({ ...params })}`),
  suggest: (q: string, limit = 6) =>
    get<SuggestItem[]>(`/search/suggest${query({ q, limit })}`),

  ranks: (period: string, params: PageParams = {}) =>
    get<PageResult<RankItem>>(`/ranks${query({ period, ...params })}`),

  rating: (novelId: number) => get<RatingSummary>(`/novels/${novelId}/rating`),
  rate: (novelId: number, score: number) =>
    put<void>(`/novels/${novelId}/rating`, { score }),
  unrate: (novelId: number) => del<void>(`/novels/${novelId}/rating`),

  reviews: (novelId: number, params: { sort?: 'hot' | 'latest'; page?: number; size?: number } = {}) =>
    get<PageResult<Review>>(`/novels/${novelId}/reviews${query({ ...params })}`),
  likeReview: (reviewId: number) => post<void>(`/reviews/${reviewId}/like`),
  unlikeReview: (reviewId: number) => del<void>(`/reviews/${reviewId}/like`),

  bookshelf: (params: PageParams = {}) =>
    get<PageResult<BookshelfItem>>(`/bookshelf${query({ ...params })}`),
  addToShelf: (novelId: number) => post<void>('/bookshelf', { novelId }),
  removeFromShelf: (novelId: number) => del<void>(`/bookshelf/${novelId}`),
  updateProgress: (novelId: number, seq: number) =>
    put<void>(`/bookshelf/${novelId}/progress`, { seq }),

  me: () => get<User>('/users/me'),
  userDetail: (id: number) => get<UserDetail>(`/users/${id}`),
  updateProfile: (body: {
    nickname?: string;
    bio?: string;
    gender?: number;
    birthday?: string;
  }) => put<User>('/users/me', body),
  userReviews: (id: number, params: PageParams = {}) =>
    get<PageResult<UserReview>>(`/users/${id}/reviews${query({ ...params })}`),
  userPosts: (id: number, params: PageParams = {}) =>
    get<PageResult<PostView>>(`/users/${id}/posts${query({ ...params })}`),
  login: (username: string, password: string) =>
    post<AuthResult>('/auth/login', { username, password }, { auth: false, retryOnUnauthorized: false }),
  register: (username: string, password: string, nickname?: string) =>
    post<AuthResult>(
      '/auth/register',
      { username, password, nickname },
      { auth: false, retryOnUnauthorized: false },
    ),
  logout: (refreshToken: string) => post<void>('/auth/logout', { refreshToken }),

  posts: (params: { sort?: 'hot' | 'latest'; novelId?: number; page?: number; size?: number } = {}) =>
    get<PageResult<PostView>>(`/posts${query({ ...params })}`),
  post: (id: number) => get<PostDetail>(`/posts/${id}`),
  createPost: (body: { title: string; content: string; coverKey?: string; refNovelIds: number[] }) =>
    post<PostDetail>('/posts', body),
  deletePost: (id: number) => del<void>(`/posts/${id}`),
  likePost: (id: number) => post<void>(`/posts/${id}/like`),
  unlikePost: (id: number) => del<void>(`/posts/${id}/like`),

  postComments: (postId: number, params: PageParams = {}) =>
    get<PageResult<PostComment>>(`/posts/${postId}/comments${query({ ...params })}`),
  createPostComment: (postId: number, content: string, parentCommentId?: number) =>
    post<PostComment>(`/posts/${postId}/comments`, { content, parentCommentId }),
  deletePostComment: (commentId: number) => del<void>(`/comments/${commentId}`),
  likePostComment: (commentId: number) => post<void>(`/comments/${commentId}/like`),
  unlikePostComment: (commentId: number) => del<void>(`/comments/${commentId}/like`),

  createReview: (novelId: number, content: string, score?: number) =>
    post<Review>(`/novels/${novelId}/reviews`, { content, score }),
  deleteReview: (reviewId: number) => del<void>(`/reviews/${reviewId}`),
  reviewReplies: (reviewId: number, params: PageParams = {}) =>
    get<PageResult<ReviewReply>>(`/reviews/${reviewId}/replies${query({ ...params })}`),
  createReply: (reviewId: number, content: string, parentReplyId?: number) =>
    post<ReviewReply>(`/reviews/${reviewId}/replies`, { content, parentReplyId }),
  deleteReply: (replyId: number) => del<void>(`/replies/${replyId}`),
  likeReply: (replyId: number) => post<void>(`/replies/${replyId}/like`),
  unlikeReply: (replyId: number) => del<void>(`/replies/${replyId}/like`),

  presign: (body: { bizType: 'COVER' | 'AVATAR' | 'POST' | 'IMPORT'; fileName: string; contentType: string }) =>
    post<PresignedUpload>('/files/presign', body),
};
