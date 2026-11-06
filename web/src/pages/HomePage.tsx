import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ArrowUpRight, Quote } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthProvider';
import {
  bookshelf,
  categoryBlurb,
  editorPicks,
  novels,
  rankItems,
  recentlyUpdated,
  RANK_TABS,
  summaryById,
  toSummary,
  topCategories,
  type RankPeriod,
} from '@/mocks';
import { formatCount, formatRelativeTime, formatWordCount } from '@/lib/format';
import { NovelCover } from '@/components/book/NovelCover';
import { NovelCard, ContinueCard, NovelRow } from '@/components/book/NovelCard';
import { SearchBox } from '@/components/search/SearchBox';
import { Segmented } from '@/components/ui/Segmented';
import { Stars } from '@/components/ui/Stars';
import { Tag, StatusDot } from '@/components/ui/Tag';

export function HomePage() {
  const { user } = useAuth();
  const [period, setPeriod] = useState<RankPeriod>('weekly');
  const ranks = rankItems(period, 8);
  const leader = ranks[0];
  const rest = ranks.slice(1);
  const featured = novels[0];
  const continueItem = bookshelf[0];
  const continueNovel = summaryById(continueItem.novelId);

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
            <div className="absolute left-[8%] top-[13%] w-[168px] -rotate-[13deg] opacity-95">
              <NovelCover novel={summaryById(6)} />
            </div>
            <div className="absolute right-[6%] top-[21%] w-[168px] rotate-[10deg] opacity-95">
              <NovelCover novel={summaryById(4)} />
            </div>
            <div className="absolute left-1/2 top-1/2 w-[228px] -translate-x-1/2 -translate-y-[54%] -rotate-[3deg]">
              <NovelCover novel={toSummary(featured)} />
            </div>

            <div className="absolute -bottom-1 left-2 flex items-center gap-4 rounded-[8px] border border-line bg-surface/90 px-4 py-3 shadow-card backdrop-blur">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-accent-soft font-serif text-[13px] text-accent">
                共
              </span>
              <div>
                <p className="text-[10.5px] tracking-[0.18em] text-faint">本周共读</p>
                <p className="mt-0.5 font-serif text-[14px] text-ink">
                  《{featured.title}》 · {featured.authorName}
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-[1240px] px-6 lg:px-10">
        {/* -------------------------------------------------------------- */}
        {/* 继续阅读                                                       */}
        {/* -------------------------------------------------------------- */}
        {user && continueNovel ? (
          <section className="border-b border-line/70 py-10">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
              <div className="shrink-0">
                <p className="text-[10.5px] tracking-[0.24em] text-faint">继续阅读</p>
                <p className="mt-1 text-[13px] text-muted">上次停在第 {continueItem.lastChapterSeq} 章</p>
              </div>
              <ContinueCard
                novel={continueNovel}
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
                options={RANK_TABS.map((tab) => ({ value: tab.value, label: tab.label }))}
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

          <div className="mt-8 grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
            {leader ? (
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
                    <span className="text-ink-soft">{leader.novel.avgRating.toFixed(1)}</span>
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
            ) : null}

            <div className="rounded-[10px] border border-line bg-surface/40 px-4 py-2">
              {rest.map((item) => (
                <NovelRow
                  key={item.novel.id}
                  novel={item.novel}
                  rankNo={item.rankNo}
                  score={item.score}
                  className="border-b border-line/60 last:border-b-0"
                />
              ))}
            </div>
          </div>
        </section>

        {/* -------------------------------------------------------------- */}
        {/* 分类                                                           */}
        {/* -------------------------------------------------------------- */}
        <section className="border-t border-line/70 py-16 lg:py-20">
          <SectionTitle eyebrow="CATEGORIES" title="按题材找书" />
          <div className="mt-8 grid grid-cols-2 gap-x-10 border-t border-line/70 md:grid-cols-4">
            {topCategories.map((category) => (
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
                    {categoryBlurb[category.id]}
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
        {/* 编辑推荐                                                       */}
        {/* -------------------------------------------------------------- */}
        <section className="border-t border-line/70 py-16 lg:py-20">
          <div className="flex items-end justify-between gap-6">
            <SectionTitle eyebrow="EDITOR'S PICK" title="编辑推荐" />
            <p className="hidden max-w-xs text-right text-[12px] leading-relaxed text-muted sm:block">
              不是算法，是几个人读完以后
              <br />
              还愿意再提一次的书。
            </p>
          </div>
          <div className="mt-8 grid grid-cols-2 gap-x-6 gap-y-10 sm:grid-cols-3 lg:grid-cols-6">
            {editorPicks.map((novel) => (
              <NovelCard key={novel.id} novel={toSummary(novel)} />
            ))}
          </div>
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
          <div className="mt-8 grid gap-x-12 md:grid-cols-2">
            {recentlyUpdated.map((novel) => {
              const detail = novels.find((item) => item.id === novel.id)!;
              return (
                <Link
                  key={novel.id}
                  to={`/novel/${novel.id}/chapters/${detail.latestChapterSeq}`}
                  className="group flex items-center gap-4 border-b border-line/70 py-4"
                >
                  <NovelCover novel={novel} className="w-10 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-serif text-[15px] text-ink transition-colors group-hover:text-accent">
                      {novel.title}
                    </p>
                    <p className="mt-0.5 truncate text-[11.5px] text-muted">
                      更新至 第 {detail.latestChapterSeq} 章 · {detail.latestChapterTitle}
                    </p>
                  </div>
                  <span className="shrink-0 text-[11px] text-faint">
                    {formatRelativeTime(novel.latestUpdateAt)}
                  </span>
                </Link>
              );
            })}
          </div>
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
