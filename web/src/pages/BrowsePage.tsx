import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { BookOpen, LayoutGrid, List, SlidersHorizontal } from 'lucide-react';
import type { NovelSummary } from '@/api/types';
import { useNovels } from '@/api/queries';
import { useCategoriesContext } from '@/features/categories/CategoryProvider';
import { formatCount, formatRating, formatWordCount } from '@/lib/format';
import { cn } from '@/lib/cn';
import { NovelCard } from '@/components/book/NovelCard';
import { NovelCover } from '@/components/book/NovelCover';
import { Pagination } from '@/components/ui/Pagination';
import { Segmented } from '@/components/ui/Segmented';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError } from '@/components/ui/LoadError';
import { NovelCardSkeleton, NovelRowSkeleton } from '@/components/ui/Skeleton';
import { Stars } from '@/components/ui/Stars';
import { StatusDot, Tag } from '@/components/ui/Tag';

type NovelListSort = 'latest' | 'newest' | 'wordCount' | 'hot';

const SORT_OPTIONS: { value: NovelListSort; label: string }[] = [
  { value: 'latest', label: '最近更新' },
  { value: 'newest', label: '最新上架' },
  { value: 'wordCount', label: '字数' },
  { value: 'hot', label: '热度' },
];

const SORT_VALUES = new Set(SORT_OPTIONS.map((option) => option.value));

export function BrowsePage() {
  const [params, setParams] = useSearchParams();
  const { topCategories, nameOf, isLoading: categoriesLoading } = useCategoriesContext();

  const categoryId = Number(params.get('category') ?? 0) || 0;
  const statusParam = params.get('status');
  const status =
    statusParam === '0' || statusParam === '1' ? Number(statusParam) : undefined;
  const rawSort = params.get('sort');
  const sort: NovelListSort =
    rawSort && SORT_VALUES.has(rawSort as NovelListSort) ? (rawSort as NovelListSort) : 'latest';
  const page = Math.max(1, Number(params.get('page') ?? 1) || 1);
  const [view, setView] = useState<'grid' | 'list'>('grid');

  const { data, isLoading, isError, error, refetch } = useNovels({
    categoryId: categoryId || undefined,
    status,
    sort,
    page,
    size: 12,
  });

  const result = data ?? { list: [], total: 0, page, size: 12, hasNext: false };
  const activeTop = topCategories.find((category) => category.id === categoryId);

  const update = (next: Record<string, string | number | undefined>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([key, value]) => {
      if (value === undefined || value === '' || value === 0) {
        merged.delete(key);
      } else {
        merged.set(key, String(value));
      }
    });
    if (!('page' in next)) merged.delete('page');
    setParams(merged, { replace: true });
  };

  // 旧链接可能带着子分类 id；当前数据是按顶级分类收的，落到不存在的分类就退回全部
  useEffect(() => {
    if (categoryId && !categoriesLoading && !activeTop) update({ category: undefined });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryId, categoriesLoading, activeTop]);

  return (
    <div className="mx-auto max-w-[1240px] px-6 pb-24 pt-12 lg:px-10 lg:pt-16">
      <header className="flex flex-wrap items-end justify-between gap-4 border-b border-line pb-8">
        <div>
          <p className="text-[10.5px] tracking-[0.28em] text-accent">LIBRARY</p>
          <h1 className="mt-3 font-serif text-[34px] font-semibold text-ink sm:text-[40px]">
            {categoryId ? nameOf(categoryId) : '全部书库'}
          </h1>
          <p className="mt-2 text-[12.5px] text-muted">
            共 {result.total} 本 ·{' '}
            {categoryId ? `收录于「${nameOf(categoryId)}」` : '按题材、状态与热度慢慢挑'}
          </p>
        </div>
        <p className="hidden max-w-xs text-right text-[11.5px] leading-relaxed text-faint sm:block">
          排序只看已建好索引的四个字段，
          <br />
          不提供"任意字段任意方向"。
        </p>
      </header>

      <div className="mt-10 grid gap-10 lg:grid-cols-[210px_1fr] lg:gap-14">
        {/* 分类：后端返回两级，但当前书都挂在顶级分类下，所以只展示顶级 */}
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="flex items-center gap-2 text-[10.5px] tracking-[0.24em] text-faint">
            <SlidersHorizontal size={12} />
            分类
          </div>
          <nav className="mt-4 flex flex-wrap gap-x-5 gap-y-2 lg:flex-col lg:gap-0" aria-label="分类">
            <button
              type="button"
              onClick={() => update({ category: undefined })}
              className={cn(
                'flex h-9 items-center text-left text-[13.5px] transition-colors lg:border-b lg:border-line/60 lg:pr-2',
                !categoryId ? 'text-ink' : 'text-muted hover:text-ink',
              )}
            >
              全部
            </button>
            {topCategories.map((category) => {
              const active = category.id === categoryId;
              return (
                <button
                  key={category.id}
                  type="button"
                  onClick={() => update({ category: category.id })}
                  className={cn(
                    'flex h-9 items-center gap-2 text-left text-[13.5px] transition-colors lg:w-full lg:border-b lg:border-line/60 lg:pr-2',
                    active ? 'text-accent' : 'text-muted hover:text-ink',
                  )}
                >
                  {active ? <span className="h-1 w-1 rounded-full bg-accent" /> : null}
                  {category.name}
                </button>
              );
            })}
          </nav>
        </aside>

        {/* 结果区 */}
        <div>
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-line pb-4">
            <div className="flex items-center gap-2">
              {[
                { label: '全部状态', value: undefined },
                { label: '连载中', value: 0 },
                { label: '已完结', value: 1 },
              ].map((option) => {
                const active = status === option.value;
                return (
                  <button
                    key={option.label}
                    type="button"
                    onClick={() => update({ status: option.value })}
                    className={cn(
                      'h-8 rounded-full px-3 text-[12.5px] transition-colors',
                      active ? 'bg-ink text-paper' : 'text-muted hover:bg-paper-deep hover:text-ink',
                    )}
                  >
                    {option.label}
                  </button>
                );
              })}
            </div>

            <div className="flex items-center gap-3">
              <Segmented
                options={SORT_OPTIONS}
                value={sort}
                onChange={(value) => update({ sort: value })}
                size="sm"
                className="hidden sm:inline-flex"
              />
              <select
                aria-label="排序方式"
                value={sort}
                onChange={(event) => update({ sort: event.target.value })}
                className="h-8 rounded-full border border-line bg-transparent px-2.5 text-[12px] text-muted sm:hidden"
              >
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              <div className="flex items-center gap-1 rounded-full border border-line p-0.5">
                <button
                  type="button"
                  onClick={() => setView('grid')}
                  aria-label="网格视图"
                  aria-pressed={view === 'grid'}
                  className={cn(
                    'inline-flex h-7 w-7 items-center justify-center rounded-full transition-colors',
                    view === 'grid' ? 'bg-ink text-paper' : 'text-muted hover:text-ink',
                  )}
                >
                  <LayoutGrid size={13} />
                </button>
                <button
                  type="button"
                  onClick={() => setView('list')}
                  aria-label="列表视图"
                  aria-pressed={view === 'list'}
                  className={cn(
                    'inline-flex h-7 w-7 items-center justify-center rounded-full transition-colors',
                    view === 'list' ? 'bg-ink text-paper' : 'text-muted hover:text-ink',
                  )}
                >
                  <List size={13} />
                </button>
              </div>
            </div>
          </div>

          {isError ? (
            <LoadError className="mt-10" error={error} onRetry={() => void refetch()} />
          ) : isLoading ? (
            view === 'grid' ? (
              <div className="mt-8 grid grid-cols-2 gap-x-6 gap-y-10 sm:grid-cols-3 xl:grid-cols-4">
                {Array.from({ length: 12 }, (_, index) => (
                  <NovelCardSkeleton key={index} />
                ))}
              </div>
            ) : (
              <div className="mt-4">
                {Array.from({ length: 5 }, (_, index) => (
                  <NovelRowSkeleton key={index} />
                ))}
              </div>
            )
          ) : result.list.length === 0 ? (
            <EmptyState
              className="mt-10"
              icon={<BookOpen size={26} strokeWidth={1.2} />}
              title="这里还没有书"
              description="换个分类或状态试试。"
            />
          ) : view === 'grid' ? (
            <div className="mt-8 grid grid-cols-2 gap-x-6 gap-y-10 sm:grid-cols-3 xl:grid-cols-4">
              {result.list.map((novel) => (
                <NovelCard key={novel.id} novel={novel} />
              ))}
            </div>
          ) : (
            <div className="mt-4">
              {result.list.map((novel) => (
                <BrowseRow key={novel.id} novel={novel} />
              ))}
            </div>
          )}

          <Pagination
            className="mt-14"
            page={result.page}
            total={result.total}
            size={result.size}
            onChange={(next) => update({ page: next })}
          />
        </div>
      </div>
    </div>
  );
}

function BrowseRow({ novel }: { novel: NovelSummary }) {
  const { nameOf } = useCategoriesContext();
  return (
    <Link
      to={`/novel/${novel.id}`}
      className="group flex gap-5 border-b border-line/70 py-6 first:pt-2"
    >
      <NovelCover novel={novel} className="w-16 shrink-0" />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <h3 className="font-serif text-[17px] text-ink transition-colors group-hover:text-accent">
            {novel.title}
          </h3>
          <span className="text-[12px] text-muted">{novel.authorName}</span>
          <StatusDot status={novel.status} />
        </div>
        <p className="mt-2 line-clamp-2 text-[12.5px] leading-[1.85] text-muted">{novel.intro}</p>
        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11.5px] text-faint">
          <span className="flex items-center gap-1.5">
            <Stars value={novel.avgRating} size={11} />
            <span className="text-ink-soft">{formatRating(novel.avgRating)}</span>
          </span>
          <span>{formatWordCount(novel.wordCount)}</span>
          <span>{novel.chapterCount} 章</span>
          <span>{formatCount(novel.viewCount)} 阅读</span>
          <Tag>{nameOf(novel.categoryId)}</Tag>
        </div>
      </div>
    </Link>
  );
}
