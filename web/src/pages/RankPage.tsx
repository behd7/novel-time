import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, TrendingUp } from 'lucide-react';
import { rankItems, RANK_TABS, type RankPeriod } from '@/mocks';
import { formatCount, formatRating } from '@/lib/format';
import { cn } from '@/lib/cn';
import { NovelCover } from '@/components/book/NovelCover';
import { NovelRow } from '@/components/book/NovelCard';
import { Segmented } from '@/components/ui/Segmented';
import { Stars } from '@/components/ui/Stars';

const CURRENT = RANK_TABS.find((tab) => tab.value === 'weekly')!;

export function RankPage() {
  const [period, setPeriod] = useState<RankPeriod>('weekly');
  const items = rankItems(period, 10);
  const [first, second, third] = items;
  const rest = items.slice(3);
  const tab = RANK_TABS.find((item) => item.value === period) ?? CURRENT;

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

      <p className="mt-10 text-[11.5px] leading-relaxed text-faint">
        日 / 周 / 月榜读的是 Redis ZSET 里的实时热度，总榜读的是数据库里累计出来的分数；
        两者的尺度不同，所以页面上只比名次，不跨榜比分数。
      </p>
    </div>
  );
}
