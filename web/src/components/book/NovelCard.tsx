import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import type { NovelSummary } from '@/api/types';
import { categoryName } from '@/mocks/categories';
import { formatCount, formatRating, readingPercent } from '@/lib/format';
import { cn } from '@/lib/cn';
import { NovelCover } from './NovelCover';
import { Stars } from '@/components/ui/Stars';

interface NovelCardProps {
  novel: NovelSummary;
  className?: string;
  showRating?: boolean;
}

export function NovelCard({ novel, className, showRating = true }: NovelCardProps) {
  return (
    <Link to={`/novel/${novel.id}`} className={cn('group block', className)}>
      <div className="transition-transform duration-500 ease-soft group-hover:-translate-y-1">
        <NovelCover novel={novel} />
      </div>
      <h3 className="mt-3 truncate font-serif text-[16px] font-medium leading-snug text-ink transition-colors group-hover:text-accent">
        {novel.title}
      </h3>
      <p className="mt-1 flex items-center gap-1.5 truncate text-xs text-muted">
        <span className="truncate">{novel.authorName}</span>
        <span className="text-line-strong">·</span>
        <span className="shrink-0">{categoryName(novel.categoryId)}</span>
      </p>
      {showRating ? (
        <div className="mt-1.5 flex items-center gap-1.5 text-xs text-muted">
          <Stars value={novel.avgRating} size={12} />
          <span className="text-ink-soft">{formatRating(novel.avgRating)}</span>
          <span className="text-faint">({formatCount(novel.ratingCount)})</span>
        </div>
      ) : null}
    </Link>
  );
}

interface NovelRowProps {
  novel: NovelSummary;
  rankNo?: number;
  score?: number;
  className?: string;
  showView?: boolean;
}

/** 榜单 / 侧栏用的横排条目：一屏能放下十本，比卡片网格更适合"比较名次"。 */
export function NovelRow({ novel, rankNo, score, className, showView = false }: NovelRowProps) {
  return (
    <Link
      to={`/novel/${novel.id}`}
      className={cn(
        'group flex items-center gap-3 rounded-[6px] px-1 py-2.5 transition-colors hover:bg-paper-deep/60',
        className,
      )}
    >
      {rankNo != null ? (
        <span
          className={cn(
            'w-6 shrink-0 text-center font-serif text-[15px] tabular-nums',
            rankNo <= 3 ? 'text-accent' : 'text-faint',
          )}
        >
          {String(rankNo).padStart(2, '0')}
        </span>
      ) : null}
      <NovelCover novel={novel} className="w-9 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13.5px] font-medium text-ink transition-colors group-hover:text-accent">
          {novel.title}
        </p>
        <p className="mt-0.5 truncate text-[11.5px] text-muted">
          {novel.authorName} · {categoryName(novel.categoryId)}
        </p>
      </div>
      {score != null ? (
        <span className="shrink-0 font-serif text-[13px] tabular-nums text-muted">
          {formatCount(score)}
        </span>
      ) : showView ? (
        <span className="shrink-0 text-[11.5px] tabular-nums text-faint">
          {formatCount(novel.viewCount)} 阅读
        </span>
      ) : null}
    </Link>
  );
}

interface ContinueCardProps {
  novel: NovelSummary;
  lastChapterSeq: number;
  totalChapters: number;
  className?: string;
}

/** 「继续阅读」：把"读到哪、还剩多少"摊开说，比只给一个按钮更有推进感。 */
export function ContinueCard({ novel, lastChapterSeq, totalChapters, className }: ContinueCardProps) {
  const percent = readingPercent(lastChapterSeq, totalChapters);
  return (
    <div
      className={cn(
        'flex items-center gap-5 rounded-[10px] border border-line bg-surface/70 p-4',
        className,
      )}
    >
      <NovelCover novel={novel} className="w-12 shrink-0" />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-3">
          <p className="truncate font-serif text-[15px] text-ink">{novel.title}</p>
          <span className="shrink-0 text-[11px] tabular-nums text-faint">
            {lastChapterSeq} / {totalChapters} 章
          </span>
        </div>
        <div className="mt-2 h-[3px] w-full overflow-hidden rounded-full bg-line">
          <div className="h-full bg-accent" style={{ width: `${percent}%` }} />
        </div>
        <p className="mt-1.5 text-[11.5px] text-muted">
          读到第 {lastChapterSeq} 章 · 还剩 {totalChapters - lastChapterSeq} 章
        </p>
      </div>
      <Link
        to={`/novel/${novel.id}/chapters/${lastChapterSeq}`}
        className="inline-flex shrink-0 items-center gap-1 text-[12.5px] font-medium text-accent hover:text-accent-deep"
      >
        继续
        <ArrowRight size={13} />
      </Link>
    </div>
  );
}
