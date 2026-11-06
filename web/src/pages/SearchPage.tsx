import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { SearchX } from 'lucide-react';
import { categoryName, searchNovels, topCategories, type SearchSort } from '@/mocks';
import { formatCount, formatRating, formatRelativeTime, formatWordCount } from '@/lib/format';
import { cn } from '@/lib/cn';
import { NovelCover } from '@/components/book/NovelCover';
import { SearchBox } from '@/components/search/SearchBox';
import { Segmented } from '@/components/ui/Segmented';
import { Pagination } from '@/components/ui/Pagination';
import { EmptyState } from '@/components/ui/EmptyState';
import { NovelRowSkeleton } from '@/components/ui/Skeleton';
import { Stars } from '@/components/ui/Stars';
import { StatusDot, Tag } from '@/components/ui/Tag';

const SORT_OPTIONS: { value: SearchSort; label: string }[] = [
  { value: 'relevance', label: '相关度' },
  { value: 'latest', label: '最近更新' },
  { value: 'hot', label: '最热' },
  { value: 'rating', label: '评分' },
  { value: 'wordCount', label: '字数' },
];

const HOT_KEYWORDS = ['长安', '悬疑', '沈砚', '修真', '星际', '古言'];

export function SearchPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const categoryId = Number(params.get('category') ?? 0) || 0;
  const statusParam = params.get('status');
  const status = statusParam === null ? undefined : Number(statusParam);
  const sort = (params.get('sort') as SearchSort | null) ?? 'relevance';
  const page = Math.max(1, Number(params.get('page') ?? 1) || 1);
  const [pending, setPending] = useState(false);

  const result = useMemo(
    () =>
      searchNovels({
        q,
        categoryId: categoryId || undefined,
        status,
        sort,
        page,
        size: 8,
      }),
    [q, categoryId, status, sort, page],
  );

  useEffect(() => {
    if (!q) return;
    setPending(true);
    const timer = window.setTimeout(() => setPending(false), 180);
    return () => window.clearTimeout(timer);
  }, [q, categoryId, status, sort, page]);

  const update = (next: Record<string, string | number | undefined>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([key, value]) => {
      if (value === undefined || value === '' || value === 0) merged.delete(key);
      else merged.set(key, String(value));
    });
    if (!('page' in next)) merged.delete('page');
    setParams(merged, { replace: true });
  };

  return (
    <div className="mx-auto max-w-[1040px] px-6 pb-24 pt-12 lg:px-10 lg:pt-16">
      <p className="text-[10.5px] tracking-[0.28em] text-accent">SEARCH</p>
      <h1 className="mt-3 font-serif text-[34px] font-semibold text-ink sm:text-[40px]">
        {q ? `“${q}”的搜索结果` : '找一本书'}
      </h1>

      <SearchBox
        variant="page"
        initialValue={q}
        className="mt-8"
        placeholder="书名、作者、简介里的任意词"
        onSubmit={(keyword) => update({ q: keyword, page: undefined })}
        autoFocus={!q}
      />

      {!q ? (
        <div className="mt-14">
          <p className="text-[11px] tracking-[0.2em] text-faint">热门搜索</p>
          <div className="mt-4 flex flex-wrap gap-2">
            {HOT_KEYWORDS.map((keyword) => (
              <Link
                key={keyword}
                to={`/search?q=${encodeURIComponent(keyword)}`}
                className="rounded-full border border-line px-4 py-2 text-[13px] text-ink-soft transition-colors hover:border-line-strong hover:bg-surface"
              >
                {keyword}
              </Link>
            ))}
          </div>

          <div className="mt-16 border-t border-line pt-8">
            <p className="font-serif text-lg text-ink">搜索是怎么工作的</p>
            <p className="mt-3 max-w-2xl text-[13px] leading-[1.9] text-muted">
              关键词先经过 IK 分词，再在书名（权重 3）、作者（权重 2）与简介之间做
              multi_match；命中的片段由引擎返回高亮，前端不自己找关键字——
              中文分词后的命中和字面匹配经常不是一回事。
            </p>
          </div>
        </div>
      ) : (
        <>
          <div className="mt-10 flex flex-wrap items-center gap-2 border-b border-line pb-5">
            <FilterChip active={!categoryId} onClick={() => update({ category: undefined })}>
              全部分类
            </FilterChip>
            {topCategories.map((category) => (
              <FilterChip
                key={category.id}
                active={categoryId === category.id}
                onClick={() => update({ category: category.id })}
              >
                {category.name}
              </FilterChip>
            ))}
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-between gap-4">
            <p className="text-[12px] text-muted">
              找到 <span className="text-ink">{result.total}</span> 本相关作品
              {categoryId ? ` · ${categoryName(categoryId)}` : ''}
            </p>
            <div className="flex items-center gap-2">
              <div className="hidden items-center gap-1 sm:flex">
                {[
                  { label: '不限', value: undefined },
                  { label: '连载', value: 0 },
                  { label: '完结', value: 1 },
                ].map((option) => (
                  <button
                    key={option.label}
                    type="button"
                    onClick={() => update({ status: option.value })}
                    className={cn(
                      'h-7 rounded-full px-2.5 text-[11.5px] transition-colors',
                      status === option.value
                        ? 'bg-ink text-paper'
                        : 'text-muted hover:bg-paper-deep hover:text-ink',
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
              <Segmented
                options={SORT_OPTIONS}
                value={sort}
                onChange={(value) => update({ sort: value })}
                size="sm"
              />
            </div>
          </div>

          <div className="mt-4">
            {pending ? (
              <div className="divide-y divide-line/60">
                <NovelRowSkeleton />
                <NovelRowSkeleton />
                <NovelRowSkeleton />
              </div>
            ) : result.list.length === 0 ? (
              <EmptyState
                className="mt-8"
                icon={<SearchX size={26} strokeWidth={1.2} />}
                title="没有找到相关作品"
                description={
                  <>
                    试试更短的关键词，或去掉分类与状态筛选。
                    <br />
                    搜索索引由 ES 提供，MySQL 才是真相源；刚发布的新书可能还会慢半拍。
                  </>
                }
                action={
                  <button
                    type="button"
                    onClick={() => setParams({ q }, { replace: true })}
                    className="text-[12.5px] text-accent hover:text-accent-deep"
                  >
                    清除全部筛选
                  </button>
                }
              />
            ) : (
              <div className="divide-y divide-line/60">
                {result.list.map((hit) => (
                  <Link
                    key={hit.novelId}
                    to={`/novel/${hit.novelId}`}
                    className="group flex gap-5 py-7 first:pt-4"
                  >
                    <NovelCover
                      novel={{
                        title: hit.title,
                        authorName: hit.authorName,
                        coverKey: hit.coverKey,
                        categoryId: hit.categoryId,
                      }}
                      className="w-[76px] shrink-0"
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                        <h2 className="font-serif text-[19px] text-ink transition-colors group-hover:text-accent">
                          <Highlight html={hit.highlight?.title} fallback={hit.title} />
                        </h2>
                        <span className="text-[12px] text-muted">
                          <Highlight html={hit.highlight?.authorName} fallback={hit.authorName} />
                        </span>
                        <StatusDot status={hit.status} />
                      </div>
                      <p className="mt-2 line-clamp-2 text-[13px] leading-[1.85] text-muted">
                        <Highlight html={hit.highlight?.intro} fallback={hit.intro} />
                      </p>
                      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[11.5px] text-faint">
                        <span className="flex items-center gap-1.5">
                          <Stars value={hit.avgRating} size={11} />
                          <span className="text-ink-soft">{formatRating(hit.avgRating)}</span>
                          <span>({formatCount(hit.ratingCount)})</span>
                        </span>
                        <span>{formatWordCount(hit.wordCount)}</span>
                        <span>{hit.chapterCount} 章</span>
                        <span>{formatCount(hit.viewCount)} 阅读</span>
                        <span>{formatRelativeTime(hit.latestUpdateAt)}更新</span>
                        <Tag>{hit.categoryName}</Tag>
                      </div>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </div>

          <Pagination
            className="mt-12"
            page={result.page}
            total={result.total}
            size={result.size}
            onChange={(next) => update({ page: next })}
          />
        </>
      )}
    </div>
  );
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'h-8 rounded-full px-3.5 text-[12.5px] transition-colors',
        active ? 'bg-ink text-paper' : 'text-muted hover:bg-paper-deep hover:text-ink',
      )}
    >
      {children}
    </button>
  );
}

/**
 * ES 返回的高亮是 HTML 片段。这里只保留 <em>，其余标签与属性全部剥掉：
 * 高亮内容来自用户输入过的关键词与被索引的正文，不能当作可信 HTML 直接插入。
 */
function sanitize(html: string): string {
  return html
    .replace(/<em[^>]*>/gi, '<em>')
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<(?!\/?em>)[^>]*>/gi, '');
}

function Highlight({ html, fallback }: { html: string | null | undefined; fallback: string }) {
  if (!html) return <>{fallback}</>;
  return (
    <span
      className="highlight"
      // 已通过 sanitize 只保留 em 标签
      dangerouslySetInnerHTML={{ __html: sanitize(html) }}
    />
  );
}
