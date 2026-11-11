import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, TrendingUp } from 'lucide-react';
import { useRanks } from '@/api/queries';
import { formatCount, formatRating } from '@/lib/format';
import { cn } from '@/lib/cn';
import { NovelCover } from '@/components/book/NovelCover';
import { NovelRow } from '@/components/book/NovelCard';
import { Segmented } from '@/components/ui/Segmented';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError } from '@/components/ui/LoadError';
import { Skeleton } from '@/components/ui/Skeleton';
import { Stars } from '@/components/ui/Stars';

type RankPeriod = 'daily' | 'weekly' | 'monthly' | 'all';

const RANK_TABS: { value: RankPeriod; label: string; hint: string }[] = [
  { value: 'daily', label: '日榜', hint: '过去 24 小时' },
  { value: 'weekly', label: '周榜', hint: '本周（ISO 周）' },
  { value: 'monthly', label: '月榜', hint: '本月' },
  { value: 'all', label: '总榜', hint: '累计热度' },
];

export function RankPage() {
  const [period, setPeriod] = useState<RankPeriod>('weekly');
  const { data, isLoading, isError, error, refetch } = useRanks(period, { page: 1, size: 10 });

  const items = data?.list ?? [];
  const [first, second, third] = items;
  const rest = items.slice(3);
  const tab = RANK_TABS.find((item) => item.value === period) ?? RANK_TABS[1];

  return (
    <div className="mx-auto max-w-[1240px] px-6 pb-24 pt-12 lg:px-10 lg:pt-16">
      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-line pb-8">
        <div>
          <p className="text-[10.5px] tracking-[0.28em] text-accent">RANKING</p>
          <h1 className="mt-3 font-serif text-[34px] font-semibold text-ink sm:text-[40px]">
            热门榜单
          </h1>
          <p className="mt-2 text-[12.5px] text-muted">
            {tab.hint} · 由结算任务生成，最多滞后一个刷新周期
          </p>
        </div>
        <Segmented
          options={RANK_TABS.map((item) => ({ value: item.value, label: item.label, hint: item.hint }))}
          value={period}
          onChange={setPeriod}
        />
      </header>

      {isError ? (
        <LoadError className="mt-10" error={error} onRetry={() => void refetch()} />
      ) : isLoading ? (
        <>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            {Array.from({ length: 3 }, (_, index) => (
              <div key={index} className="rounded-[10px] border border-line p-6">
                <Skeleton className="h-10 w-14" />
                <div className="mt-5 flex gap-4">
                  <Skeleton className="aspect-[3/4] w-20" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-5 w-2/3" />
                    <Skeleton className="h-3 w-1/3" />
                    <Skeleton className="h-3 w-1/2" />
                  </div>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-12 space-y-4">
            {Array.from({ length: 5 }, (_, index) => (
              <Skeleton key={index} className="h-14 w-full" />
            ))}
          </div>
        </>
      ) : items.length === 0 ? (
        <EmptyState
          className="mt-10"
          title="这个周期还没有榜单"
          description="榜单每 10 分钟结算一次；新书需要先产生阅读、收藏或书评，才会进入榜单。"
        />
      ) : (
        <>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            {[second, first, third].filter(Boolean).map((item) => {
              const isFirst = item.rankNo === 1;
              return (
                <Link
                  key={item.novel.id}
                  to={`/novel/${item.novel.id}`}
                  className={cn(
                    'group relative flex flex-col rounded-[10px] border p-6 transition-colors',
                    isFirst
                      ? 'border-accent/30 bg-accent-soft/40 md:-mt-4 md:pb-10'
                      : 'border-line bg-surface/50 hover:border-line-strong',
                  )}
                >
                  <div className="flex items-start justify-between">
                    <span
                      className={cn(
                        'font-serif leading-none',
                        isFirst ? 'text-[52px] text-accent/40' : 'text-[40px] text-line-strong',
                      )}
                    >
                      {String(item.rankNo).padStart(2, '0')}
                    </span>
                    {isFirst ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-accent px-2.5 py-1 text-[10px] tracking-[0.1em] text-paper">
                        <TrendingUp size={11} />
                        榜首
                      </span>
                    ) : null}
                  </div>

                  <div className={cn('flex gap-4', isFirst ? 'mt-4' : 'mt-3')}>
                    <NovelCover novel={item.novel} className={isFirst ? 'w-28' : 'w-20'} />
                    <div className="min-w-0">
                      <h2
                        className={cn(
                          'font-serif text-ink transition-colors group-hover:text-accent',
                          isFirst ? 'text-[21px]' : 'text-[17px]',
                        )}
                      >
                        {item.novel.title}
                      </h2>
                      <p className="mt-1 truncate text-[11.5px] text-muted">{item.novel.authorName}</p>
                      <div className="mt-2 flex items-center gap-1.5 text-[11.5px] text-muted">
                        <Stars value={item.novel.avgRating} size={11} />
                        <span>{formatRating(item.novel.avgRating)}</span>
                      </div>
                      <p className="mt-2 text-[11px] text-faint">热度 {formatCount(item.score)}</p>
                    </div>
                  </div>

                  <p className="mt-4 line-clamp-2 text-[12px] leading-[1.8] text-muted">
                    {item.novel.intro}
                  </p>
                  <span className="mt-auto flex items-center gap-1 pt-4 text-[11.5px] text-accent">
                    开始阅读
                    <ArrowUpRight size={12} />
                  </span>
                </Link>
              );
            })}
          </div>

          {rest.length > 0 ? (
            <section className="mt-12">
              <div className="flex items-center justify-between border-b border-line pb-3">
                <p className="text-[10.5px] tracking-[0.24em] text-faint">04 — 10</p>
                <p className="text-[11px] text-faint">热度分只在同一周期内可比</p>
              </div>
              <div className="divide-y divide-line/60">
                {rest.map((item) => (
                  <NovelRow
                    key={item.novel.id}
                    novel={item.novel}
                    rankNo={item.rankNo}
                    score={item.score}
                    className="py-4"
                  />
                ))}
              </div>
            </section>
          ) : null}
        </>
      )}

      <p className="mt-10 text-[11.5px] leading-relaxed text-faint">
        日 / 周 / 月榜读的是 Redis ZSET 里的实时热度，总榜读的是数据库里累计出来的分数；
        两者的尺度不同，所以页面上只比名次，不跨榜比分数。
      </p>
    </div>
  );
}
