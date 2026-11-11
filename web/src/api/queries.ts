import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useAuth } from '@/features/auth/AuthProvider';
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

export function useBookshelf() {
  const { isAuthenticated } = useAuth();
  return useQuery({
    queryKey: qk.bookshelf,
    queryFn: () => api.bookshelf({ page: 1, size: 20 }),
    enabled: isAuthenticated,
    staleTime: 30_000,
  });
}
