import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowDownUp, ArrowRight, BookOpen, CornerDownRight } from 'lucide-react';
import {
  findBookshelfItem,
  getNovel,
  listChapters,
  toSummary,
} from '@/mocks';
import { useAuth } from '@/features/auth/AuthProvider';
import { formatRelativeTime, readingPercent } from '@/lib/format';
import { loadProgress } from '@/lib/progress';
import { cn } from '@/lib/cn';
import { NovelCover } from '@/components/book/NovelCover';
import { Pagination } from '@/components/ui/Pagination';
import { ButtonLink } from '@/components/ui/Button';
import { StatusDot, Tag } from '@/components/ui/Tag';
import { NotFoundPage } from './NotFoundPage';

const PAGE_SIZE = 30;

export function CatalogPage() {
  const { id } = useParams();
  const novelId = Number(id);
  const novel = getNovel(novelId);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const page = Math.max(1, Number(params.get('page') ?? 1) || 1);
  const [desc, setDesc] = useState(false);
  const [jump, setJump] = useState('');

  const result = useMemo(() => listChapters(novelId, page, PAGE_SIZE), [novelId, page]);

  if (!novel) {
    return <NotFoundPage />;
  }

  const shelfItem = user ? findBookshelfItem(novelId) : undefined;
  const lastRead = loadProgress(novelId) ?? shelfItem?.lastChapterSeq ?? null;
  const chapters = desc ? [...result.list].reverse() : result.list;

  const goPage = (next: number) => {
    const merged = new URLSearchParams(params);
    merged.set('page', String(next));
    setParams(merged, { replace: true });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const submitJump = () => {
    const seq = Number(jump);
    if (!Number.isInteger(seq) || seq < 1 || seq > novel.chapterCount) return;
    navigate(`/novel/${novel.id}/chapters/${seq}`);
  };

  return (
    <div className="mx-auto max-w-[1040px] px-6 pb-24 pt-8 lg:px-10 lg:pt-10">
      <nav className="flex items-center gap-2 text-[11.5px] text-muted" aria-label="面包屑">
        <Link to={`/novel/${novel.id}`} className="transition-colors hover:text-ink">
          {novel.title}
        </Link>
        <span className="text-faint">/</span>
        <span className="text-ink-soft">目录</span>
      </nav>

      <header className="mt-6 flex flex-wrap items-center gap-6 border-b border-line pb-8">
        <NovelCover novel={toSummary(novel)} className="w-16 shrink-0" />
        <div className="min-w-0 flex-1">
          <h1 className="font-serif text-[26px] font-semibold text-ink sm:text-[30px]">
            {novel.title}
          </h1>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px] text-muted">
            <span>{novel.authorName} 著</span>
            <StatusDot status={novel.status} />
            <span>共 {novel.chapterCount} 章</span>
            <span>最后更新 {formatRelativeTime(novel.latestUpdateAt)}</span>
          </div>
          {shelfItem && lastRead ? (
            <div className="mt-3 flex items-center gap-3">
              <div className="h-[3px] w-40 overflow-hidden rounded-full bg-line">
                <div
                  className="h-full bg-accent"
                  style={{ width: `${readingPercent(lastRead, novel.chapterCount)}%` }}
                />
              </div>
              <span className="text-[11.5px] text-muted">读到第 {lastRead} 章</span>
            </div>
          ) : null}
        </div>
        <ButtonLink to={`/novel/${novel.id}/chapters/${lastRead ?? 1}`} size="md">
          {lastRead ? '继续阅读' : '开始阅读'}
          <ArrowRight size={14} />
        </ButtonLink>
      </header>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setDesc((value) => !value)}
            className="inline-flex h-8 items-center gap-1.5 rounded-full border border-line px-3 text-[12px] text-muted transition-colors hover:border-line-strong hover:text-ink"
          >
            <ArrowDownUp size={12} />
            {desc ? '倒序' : '正序'}
          </button>
          <span className="text-[11.5px] text-faint">
            第 {result.page} / {Math.max(1, Math.ceil(result.total / PAGE_SIZE))} 页
          </span>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex h-8 items-center gap-1 rounded-full border border-line px-2.5">
            <CornerDownRight size={12} className="text-faint" />
            <input
              value={jump}
              onChange={(event) => setJump(event.target.value.replace(/\D/g, ''))}
              onKeyDown={(event) => {
                if (event.key === 'Enter') submitJump();
              }}
              placeholder="跳转到章"
              className="w-20 bg-transparent text-[12px] text-ink placeholder:text-faint focus:outline-none"
              inputMode="numeric"
              aria-label="跳转到章节"
            />
            <button
              type="button"
              onClick={submitJump}
              className="text-[11.5px] text-accent transition-colors hover:text-accent-deep"
            >
              跳转
            </button>
          </div>
        </div>
      </div>

      <div className="mt-4 grid gap-x-10 sm:grid-cols-2 lg:grid-cols-3">
        {chapters.map((chapter) => {
          const current = chapter.seq === lastRead;
          return (
            <Link
              key={chapter.seq}
              to={`/novel/${novel.id}/chapters/${chapter.seq}`}
              className={cn(
                'group flex items-center gap-3 border-b border-line/60 py-3',
                current && 'bg-accent-soft/50',
              )}
            >
              <span
                className={cn(
                  'w-9 shrink-0 text-right font-serif text-[12px] tabular-nums',
                  current ? 'text-accent' : 'text-faint',
                )}
              >
                {chapter.seq}
              </span>
              <span
                className={cn(
                  'min-w-0 flex-1 truncate text-[13.5px] transition-colors group-hover:text-accent',
                  current ? 'text-accent' : 'text-ink-soft',
                )}
              >
                {chapter.title}
              </span>
              {current ? (
                <Tag tone="accent">读到</Tag>
              ) : (
                <span className="shrink-0 text-[10.5px] text-faint">{chapter.wordCount} 字</span>
              )}
            </Link>
          );
        })}
      </div>

      <Pagination
        className="mt-10"
        page={result.page}
        total={result.total}
        size={PAGE_SIZE}
        onChange={goPage}
      />

      <div className="mt-12 flex items-center justify-center gap-2 text-[11.5px] text-faint">
        <BookOpen size={13} />
        目录按 30 章一页；阅读器里可以直接翻到上一章 / 下一章。
      </div>
    </div>
  );
}
