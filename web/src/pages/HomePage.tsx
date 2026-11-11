import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ArrowUpRight, Quote } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthProvider';
import { useBookshelf, useNovels, useRanks } from '@/api/queries';
import { useCategoriesContext } from '@/features/categories/CategoryProvider';
import { formatCount, formatRating, formatRelativeTime, formatWordCount } from '@/lib/format';
import { NovelCover } from '@/components/book/NovelCover';
import { NovelCard, ContinueCard, NovelRow } from '@/components/book/NovelCard';
import { SearchBox } from '@/components/search/SearchBox';
import { Segmented } from '@/components/ui/Segmented';
import { Stars } from '@/components/ui/Stars';
import { Tag, StatusDot } from '@/components/ui/Tag';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError } from '@/components/ui/LoadError';
import { NovelCardSkeleton, NovelRowSkeleton, Skeleton } from '@/components/ui/Skeleton';

type RankPeriod = 'daily' | 'weekly' | 'monthly' | 'all';

const RANK_TABS: { value: RankPeriod; label: string }[] = [
  { value: 'daily', label: '日榜' },
  { value: 'weekly', label: '周榜' },
  { value: 'monthly', label: '月榜' },
  { value: 'all', label: '总榜' },
];

export function HomePage() {
  const { user } = useAuth();
  const [period, setPeriod] = useState<RankPeriod>('weekly');
  const { topCategories, childrenOf, isLoading: categoriesLoading } = useCategoriesContext();

  const hot = useNovels({ sort: 'hot', size: 6 });
  const latest = useNovels({ sort: 'latest', size: 6 });
  const ranks = useRanks(period, { page: 1, size: 8 });
  const shelf = useBookshelf();

  const hotList = hot.data?.list ?? [];
  const latestList = latest.data?.list ?? [];
  const rankList = ranks.data?.list ?? [];
  const [leader, ...rest] = rankList;
  const continueItem = shelf.data?.list[0];

  // 首屏三张封面：优先热度榜，数据不够再用最近更新补齐
  const seen = new Set<number>();
  const covers = [...hotList, ...latestList]
    .filter((novel) => {
      if (seen.has(novel.id)) return false;
      seen.add(novel.id);
      return true;
    })
    .slice(0, 3);
  const [featured, leftCover, rightCover] = covers;
  const heroLoading = hot.isLoading && latest.isLoading;

  return (
    <>
      {/* ---------------------------------------------------------------- */}
      {/* Hero                                                             */}
      {/* ---------------------------------------------------------------- */}
      <section className="relative overflow-hidden border-b border-line/70">
        <div className="pointer-events-none absolute inset-0" aria-hidden>
          <div className="absolute -right-40 -top-52 h-[560px] w-[560px] rounded-full bg-accent-soft/70 blur-3xl" />
          <span className="absolute -right-8 top-6 select-none font-serif text-[300px] leading-none text-ink/[0.035]">
            读
          </span>
        </div>

        <div className="relative mx-auto grid max-w-[1240px] gap-14 px-6 pb-16 pt-16 lg:grid-cols-[1.08fr_0.92fr] lg:items-center lg:px-10 lg:pb-24 lg:pt-20">
          <div>
            <p className="text-[10.5px] font-medium tracking-[0.32em] text-accent">
              A QUIET PLACE FOR READING
            </p>
            <h1 className="mt-6 font-serif text-[52px] font-semibold leading-[1.12] tracking-[-0.02em] text-ink sm:text-[62px] lg:text-[70px]">
              今晚，
              <br />
              读点什么？
            </h1>
            <p className="mt-6 max-w-[34rem] text-pretty text-[14.5px] leading-[1.95] text-muted">
              没有信息流，没有标题党。只有书，和你愿意花掉的那个晚上。
              从书库、榜单，或一句话的搜索开始。
            </p>

            <SearchBox variant="hero" className="mt-9 max-w-xl" />

            <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px] text-muted">
              <span className="text-faint">大家在搜</span>
              {['长安旧事', '悬疑', '星际', '古言'].map((keyword) => (
                <Link
                  key={keyword}
                  to={`/search?q=${encodeURIComponent(keyword)}`}
                  className="transition-colors hover:text-accent"
                >
                  {keyword}
                </Link>
              ))}
            </div>
          </div>

          <div className="relative hidden h-[440px] lg:block" aria-hidden>
            <div className="absolute left-1/2 top-1/2 h-[350px] w-[350px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-line bg-paper-deep/50" />

            {heroLoading ? (
              <div className="absolute left-1/2 top-1/2 w-[228px] -translate-x-1/2 -translate-y-[54%]">
                <Skeleton className="aspect-[3/4] w-full" />
              </div>
            ) : featured ? (
              <>
                {leftCover ? (
                  <div className="absolute left-[8%] top-[13%] w-[168px] -rotate-[13deg] opacity-95">
                    <NovelCover novel={leftCover} />
                  </div>
                ) : null}
                {rightCover ? (
                  <div className="absolute right-[6%] top-[21%] w-[168px] rotate-[10deg] opacity-95">
                    <NovelCover novel={rightCover} />
                  </div>
                ) : null}
                <div className="absolute left-1/2 top-1/2 w-[228px] -translate-x-1/2 -translate-y-[54%] -rotate-[3deg]">
                  <NovelCover novel={featured} />
                </div>

                <div className="absolute -bottom-1 left-2 flex items-center gap-4 rounded-[8px] border border-line bg-surface/90 px-4 py-3 shadow-card backdrop-blur">
                  <span className="flex h-9 w-9 items-center justify-center rounded-full bg-accent-soft font-serif text-[13px] text-accent">
                    共
                  </span>
                  <div>
                    <p className="text-[10.5px] tracking-[0.18em] text-faint">热门共读</p>
                    <p className="mt-0.5 font-serif text-[14px] text-ink">
                      《{featured.title}》 · {featured.authorName}
                    </p>
                  </div>
                </div>
              </>
            ) : null}
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-[1240px] px-6 lg:px-10">
        {/* -------------------------------------------------------------- */}
        {/* 继续阅读                                                       */}
        {/* -------------------------------------------------------------- */}
        {user && continueItem ? (
          <section className="border-b border-line/70 py-10">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
              <div className="shrink-0">
                <p className="text-[10.5px] tracking-[0.24em] text-faint">继续阅读</p>
                <p className="mt-1 text-[13px] text-muted">
                  上次停在第 {continueItem.lastChapterSeq} 章
                </p>
              </div>
              <ContinueCard
                novel={{
                  id: continueItem.novelId,
                  title: continueItem.title,
                  authorName: continueItem.authorName,
                  coverKey: continueItem.coverKey,
                  categoryId: continueItem.categoryId,
                }}
                lastChapterSeq={continueItem.lastChapterSeq}
                totalChapters={continueItem.totalChapters}
                className="flex-1"
              />
            </div>
          </section>
        ) : null}

        {/* -------------------------------------------------------------- */}
        {/* 本周热读                                                       */}
        {/* -------------------------------------------------------------- */}
        <section className="py-16 lg:py-20">
          <div className="flex flex-wrap items-end justify-between gap-5">
            <SectionTitle eyebrow="RANKING" title="本周热读" />
            <div className="flex items-center gap-4">
              <Segmented
                options={RANK_TABS}
                value={period}
                onChange={setPeriod}
                size="sm"
              />
              <Link
                to="/rank"
                className="hidden items-center gap-1 text-[12px] text-muted transition-colors hover:text-accent sm:inline-flex"
              >
                完整榜单
                <ArrowUpRight size={13} />
              </Link>
            </div>
          </div>

          {ranks.isError ? (
            <LoadError className="mt-8" error={ranks.error} onRetry={() => void ranks.refetch()} />
          ) : ranks.isLoading ? (
            <div className="mt-8 grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
              <Skeleton className="h-[260px] rounded-[10px]" />
              <div className="space-y-3">
                {Array.from({ length: 5 }, (_, index) => (
                  <NovelRowSkeleton key={index} />
                ))}
              </div>
            </div>
          ) : rankList.length === 0 ? (
            <EmptyState
              className="mt-8"
              title="这个周期还没有榜单"
              description="榜单每 10 分钟结算一次；新书产生阅读、收藏或书评后才会进入。"
            />
          ) : (
            <div className="mt-8 grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
              <Link
                to={`/novel/${leader.novel.id}`}
                className="group flex gap-6 rounded-[10px] border border-line bg-surface/60 p-5 transition-colors hover:border-line-strong sm:p-6"
              >
                <NovelCover novel={leader.novel} className="w-32 shrink-0 sm:w-40" />
                <div className="flex min-w-0 flex-col">
                  <span className="font-serif text-[38px] leading-none text-accent/25">
                    {String(leader.rankNo).padStart(2, '0')}
                  </span>
                  <h3 className="mt-3 font-serif text-[22px] leading-tight text-ink transition-colors group-hover:text-accent">
                    {leader.novel.title}
                  </h3>
                  <p className="mt-1.5 text-[12px] text-muted">
                    {leader.novel.authorName} · {formatWordCount(leader.novel.wordCount)}
                  </p>
                  <div className="mt-2 flex items-center gap-2 text-[12px] text-muted">
                    <Stars value={leader.novel.avgRating} size={13} />
                    <span className="text-ink-soft">{formatRating(leader.novel.avgRating)}</span>
                  </div>
                  <p className="mt-3 line-clamp-3 text-[12.5px] leading-[1.85] text-muted">
                    {leader.novel.intro}
                  </p>
                  <div className="mt-auto flex items-center gap-2 pt-4">
                    <Tag tone="accent">热度 {formatCount(leader.score)}</Tag>
                    <StatusDot status={leader.novel.status} />
                  </div>
                </div>
              </Link>

              <div className="rounded-[10px] border border-line bg-surface/40 px-4 py-2">
                {rest.length > 0 ? (
                  rest.map((item) => (
                    <NovelRow
                      key={item.novel.id}
                      novel={item.novel}
                      rankNo={item.rankNo}
                      score={item.score}
                      className="border-b border-line/60 last:border-b-0"
                    />
                  ))
                ) : (
                  <p className="flex h-full min-h-[120px] items-center justify-center text-[12.5px] text-muted">
                    本期只有一本上榜，下一轮结算后会多起来。
                  </p>
                )}
              </div>
            </div>
          )}
        </section>

        {/* -------------------------------------------------------------- */}
        {/* 分类                                                           */}
        {/* -------------------------------------------------------------- */}
        <section className="border-t border-line/70 py-16 lg:py-20">
          <SectionTitle eyebrow="CATEGORIES" title="按题材找书" />
          <div className="mt-8 grid grid-cols-2 gap-x-10 border-t border-line/70 md:grid-cols-4">
            {categoriesLoading
              ? Array.from({ length: 8 }, (_, index) => (
                  <div key={index} className="border-b border-line/70 py-5 pr-1">
                    <Skeleton className="h-6 w-20" />
                    <Skeleton className="mt-2 h-3 w-28" />
                  </div>
                ))
              : topCategories.map((category) => (
                  <Link
                    key={category.id}
                    to={`/browse?category=${category.id}`}
                    className="group flex items-center justify-between border-b border-line/70 py-5 pr-1 transition-colors hover:text-accent"
                  >
                    <div>
                      <p className="font-serif text-[19px] text-ink transition-colors group-hover:text-accent">
                        {category.name}
                      </p>
                      <p className="mt-1 text-[11px] leading-relaxed text-faint">
                        {childrenOf(category.id)
                          .slice(0, 3)
                          .map((child) => child.name)
                          .join(' · ') || '正在收录'}
                      </p>
                    </div>
                    <ArrowUpRight
                      size={15}
                      className="shrink-0 text-faint transition-all duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-accent"
                    />
                  </Link>
                ))}
          </div>
        </section>

        {/* -------------------------------------------------------------- */}
        {/* 热门推荐                                                       */}
        {/* -------------------------------------------------------------- */}
        <section className="border-t border-line/70 py-16 lg:py-20">
          <div className="flex items-end justify-between gap-6">
            <SectionTitle eyebrow="POPULAR" title="热门推荐" />
            <p className="hidden max-w-xs text-right text-[12px] leading-relaxed text-muted sm:block">
              按阅读、收藏与书评算出来的热度，
              <br />
              不是按发布时间排的。
            </p>
          </div>
          {hot.isError ? (
            <LoadError className="mt-8" error={hot.error} onRetry={() => void hot.refetch()} />
          ) : hot.isLoading ? (
            <div className="mt-8 grid grid-cols-2 gap-x-6 gap-y-10 sm:grid-cols-3 lg:grid-cols-6">
              {Array.from({ length: 6 }, (_, index) => (
                <NovelCardSkeleton key={index} />
              ))}
            </div>
          ) : (
            <div className="mt-8 grid grid-cols-2 gap-x-6 gap-y-10 sm:grid-cols-3 lg:grid-cols-6">
              {hotList.map((novel) => (
                <NovelCard key={novel.id} novel={novel} />
              ))}
            </div>
          )}
        </section>

        {/* -------------------------------------------------------------- */}
        {/* 最近更新                                                       */}
        {/* -------------------------------------------------------------- */}
        <section className="border-t border-line/70 py-16 lg:py-20">
          <div className="flex items-end justify-between gap-6">
            <SectionTitle eyebrow="NEW CHAPTERS" title="最近更新" />
            <Link
              to="/browse?sort=latest"
              className="inline-flex items-center gap-1 text-[12px] text-muted transition-colors hover:text-accent"
            >
              全部
              <ArrowRight size={13} />
            </Link>
          </div>
          {latest.isError ? (
            <LoadError className="mt-8" error={latest.error} onRetry={() => void latest.refetch()} />
          ) : latest.isLoading ? (
            <div className="mt-8 grid gap-x-12 md:grid-cols-2">
              {Array.from({ length: 6 }, (_, index) => (
                <NovelRowSkeleton key={index} />
              ))}
            </div>
          ) : (
            <div className="mt-8 grid gap-x-12 md:grid-cols-2">
              {latestList.map((novel) => (
                <Link
                  key={novel.id}
                  to={`/novel/${novel.id}/chapters`}
                  className="group flex items-center gap-4 border-b border-line/70 py-4"
                >
                  <NovelCover novel={novel} className="w-10 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-serif text-[15px] text-ink transition-colors group-hover:text-accent">
                      {novel.title}
                    </p>
                    <p className="mt-0.5 truncate text-[11.5px] text-muted">
                      共 {novel.chapterCount} 章 · {formatWordCount(novel.wordCount)}
                    </p>
                  </div>
                  <span className="shrink-0 text-[11px] text-faint">
                    {formatRelativeTime(novel.latestUpdateAt)}
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>

      {/* ---------------------------------------------------------------- */}
      {/* 引言带                                                           */}
      {/* ---------------------------------------------------------------- */}
      <section className="relative overflow-hidden bg-ink py-20 text-paper">
        <span className="grain opacity-20" aria-hidden />
        <div className="relative mx-auto max-w-[820px] px-6 text-center">
          <Quote size={22} className="mx-auto text-paper/30" />
          <p className="mt-7 font-serif text-[24px] leading-[1.85] tracking-[0.02em] text-paper/90 sm:text-[28px]">
            我心里一直都在暗暗设想，
            <br className="hidden sm:block" />
            天堂应该是图书馆的模样。
          </p>
          <p className="mt-6 text-[11px] tracking-[0.24em] text-paper/40">豪尔赫·路易斯·博尔赫斯</p>
        </div>
      </section>
    </>
  );
}

function SectionTitle({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <div>
      <p className="text-[10.5px] font-medium tracking-[0.28em] text-accent">{eyebrow}</p>
      <h2 className="mt-2.5 font-serif text-[26px] font-semibold text-ink sm:text-[30px]">
        {title}
      </h2>
    </div>
  );
}
