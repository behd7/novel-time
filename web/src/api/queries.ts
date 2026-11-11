import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useAuth } from '@/features/auth/AuthProvider';
import { hasRefreshToken } from './client';
import { api, type NovelListParams, type PageParams, type SearchParams } from './endpoints';

export const qk = {
  categories: ['categories'] as const,
  novels: (params: NovelListParams) => ['novels', params] as const,
  novel: (id: number) => ['novel', id] as const,
  chapters: (novelId: number, params: PageParams) => ['chapters', novelId, params] as const,
  chapter: (novelId: number, seq: number) => ['chapter', novelId, seq] as const,
  search: (params: SearchParams) => ['search', params] as const,
  suggest: (q: string) => ['suggest', q] as const,
  ranks: (period: string, params: PageParams) => ['ranks', period, params] as const,
  rating: (novelId: number) => ['rating', novelId] as const,
  reviews: (novelId: number, params: { sort?: string; page?: number; size?: number }) =>
    ['reviews', novelId, params] as const,
  bookshelf: ['bookshelf'] as const,
  userDetail: (id: number) => ['user', id] as const,
  userReviews: (id: number, params: PageParams) => ['user-reviews', id, params] as const,
  userPosts: (id: number, params: PageParams) => ['user-posts', id, params] as const,
  posts: (params: { sort?: 'hot' | 'latest'; novelId?: number; page?: number; size?: number }) =>
    ['posts', params] as const,
  post: (id: number) => ['post', id] as const,
  postComments: (postId: number, params: PageParams) =>
    ['post-comments', postId, params] as const,
  reviewReplies: (reviewId: number, params: PageParams) =>
    ['review-replies', reviewId, params] as const,
};

export function useCategories() {
  return useQuery({
    queryKey: qk.categories,
    queryFn: api.categories,
    staleTime: 10 * 60_000,
  });
}

export function useNovels(params: NovelListParams = {}, enabled = true) {
  return useQuery({
    queryKey: qk.novels(params),
    queryFn: () => api.novels(params),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
    enabled,
  });
}

export function useNovel(id: number) {
  return useQuery({
    queryKey: qk.novel(id),
    queryFn: () => api.novel(id),
    enabled: Number.isFinite(id) && id > 0,
    // 详情接口每调用一次阅读数 +1：不做窗口聚焦重取，也不允许外部预取
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  });
}

export function useChapters(id: number, params: PageParams = {}, enabled = true) {
  return useQuery({
    queryKey: qk.chapters(id, params),
    queryFn: () => api.chapters(id, params),
    enabled: enabled && Number.isFinite(id) && id > 0,
    staleTime: 60_000,
  });
}

export function useChapter(id: number, seq: number) {
  return useQuery({
    queryKey: qk.chapter(id, seq),
    queryFn: () => api.chapter(id, seq),
    enabled: Number.isFinite(id) && id > 0 && Number.isFinite(seq) && seq > 0,
    staleTime: 10 * 60_000,
  });
}

export function useSearch(params: SearchParams) {
  return useQuery({
    queryKey: qk.search(params),
    queryFn: () => api.search(params),
    enabled: params.q.trim().length > 0,
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  });
}

export function useSuggest(q: string) {
  return useQuery({
    queryKey: qk.suggest(q),
    queryFn: () => api.suggest(q, 6),
    enabled: q.trim().length > 0,
    staleTime: 30_000,
  });
}

export function useRanks(period: string, params: PageParams = {}) {
  return useQuery({
    queryKey: qk.ranks(period, params),
    queryFn: () => api.ranks(period, params),
    staleTime: 30_000,
  });
}

export function useRating(novelId: number) {
  return useQuery({
    queryKey: qk.rating(novelId),
    queryFn: () => api.rating(novelId),
    enabled: Number.isFinite(novelId) && novelId > 0,
    staleTime: 30_000,
  });
}

export function useReviews(
  novelId: number,
  params: { sort?: 'hot' | 'latest'; page?: number; size?: number } = {},
) {
  return useQuery({
    queryKey: qk.reviews(novelId, params),
    queryFn: () => api.reviews(novelId, params),
    enabled: Number.isFinite(novelId) && novelId > 0,
    staleTime: 30_000,
  });
}

export function useBookshelfPage(page = 1, size = 50) {
  const { isAuthenticated } = useAuth();
  return useQuery({
    queryKey: ['bookshelf', 'page', page, size] as const,
    queryFn: () => api.bookshelf({ page, size }),
    // 登出时 clearSession 先清令牌、state 后更新；只看 user 会让这一帧仍去取书架并吃 401
    enabled: isAuthenticated && hasRefreshToken(),
    staleTime: 30_000,
  });
}

export function useUserDetail(id: number) {
  return useQuery({
    queryKey: qk.userDetail(id),
    queryFn: () => api.userDetail(id),
    enabled: Number.isFinite(id) && id > 0,
    staleTime: 60_000,
  });
}

export function useUserReviews(id: number, page = 1, size = 6) {
  return useQuery({
    queryKey: qk.userReviews(id, { page, size }),
    queryFn: () => api.userReviews(id, { page, size }),
    enabled: Number.isFinite(id) && id > 0,
    staleTime: 30_000,
  });
}

export function useUserPosts(id: number, page = 1, size = 6) {
  return useQuery({
    queryKey: qk.userPosts(id, { page, size }),
    queryFn: () => api.userPosts(id, { page, size }),
    enabled: Number.isFinite(id) && id > 0,
    staleTime: 30_000,
  });
}

export function usePosts(
  params: { sort?: 'hot' | 'latest'; novelId?: number; page?: number; size?: number } = {},
) {
  return useQuery({
    queryKey: qk.posts(params),
    queryFn: () => api.posts(params),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}

export function usePost(id: number) {
  return useQuery({
    queryKey: qk.post(id),
    queryFn: () => api.post(id),
    enabled: Number.isFinite(id) && id > 0,
    staleTime: 60_000,
    // 帖子详情浏览数 +1，和小说详情一样不做窗口聚焦重取
    refetchOnWindowFocus: false,
  });
}

export function usePostComments(postId: number, params: PageParams = { page: 1, size: 50 }) {
  return useQuery({
    queryKey: qk.postComments(postId, params),
    queryFn: () => api.postComments(postId, params),
    enabled: Number.isFinite(postId) && postId > 0,
    staleTime: 15_000,
  });
}

export function useReviewReplies(reviewId: number, enabled = true) {
  return useQuery({
    queryKey: qk.reviewReplies(reviewId, { page: 1, size: 50 }),
    queryFn: () => api.reviewReplies(reviewId, { page: 1, size: 50 }),
    enabled: enabled && Number.isFinite(reviewId) && reviewId > 0,
    staleTime: 15_000,
  });
}

export function useBookshelf() {
  const { isAuthenticated } = useAuth();
  return useQuery({
    queryKey: qk.bookshelf,
    queryFn: () => api.bookshelf({ page: 1, size: 20 }),
    enabled: isAuthenticated && hasRefreshToken(),
    staleTime: 30_000,
  });
}
