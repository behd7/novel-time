import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { BookOpen, Library, Lock, Trash2 } from 'lucide-react';
import type { BookshelfItem } from '@/api/types';
import { api } from '@/api/endpoints';
import { useBookshelfPage } from '@/api/queries';
import { useAuth } from '@/features/auth/AuthProvider';
import { useCategoriesContext } from '@/features/categories/CategoryProvider';
import { Button, ButtonLink } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError, errorMessage } from '@/components/ui/LoadError';
import { Pagination } from '@/components/ui/Pagination';
import { Segmented, UnderlineTabs } from '@/components/ui/Segmented';
import { Skeleton } from '@/components/ui/Skeleton';
import { NovelCover } from '@/components/book/NovelCover';
import { useToast } from '@/components/ui/Toast';
import { formatRelativeTime, readingPercent } from '@/lib/format';
import { cn } from '@/lib/cn';

type ShelfTab = 'all' | 'reading' | 'finished' | 'unread';
type ShelfSort = 'read' | 'added';

const PAGE_SIZE = 12;

const SORTS: { value: ShelfSort; label: string }[] = [
  { value: 'read', label: '最近阅读' },
  { value: 'added', label: '加入时间' },
];

function isFinished(item: BookshelfItem) {
  return item.totalChapters > 0 && item.lastChapterSeq >= item.totalChapters;
}

function isUnread(item: BookshelfItem) {
  return item.lastChapterSeq <= 0;
}

export function BookshelfPage() {
  const { user, status } = useAuth();
  const { nameOf } = useCategoriesContext();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const shelfQuery = useBookshelfPage(1, 50);

  const [tab, setTab] = useState<ShelfTab>('all');
  const [sort, setSort] = useState<ShelfSort>('read');
  const [page, setPage] = useState(1);
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const [removingId, setRemovingId] = useState<number | null>(null);

  const all = shelfQuery.data?.list ?? [];
  const reading = all.filter((item) => !isFinished(item) && !isUnread(item));
  const finished = all.filter(isFinished);
  const unread = all.filter(isUnread);

  const filtered = all.filter((item) => {
    if (tab === 'reading') return !isFinished(item) && !isUnread(item);
    if (tab === 'finished') return isFinished(item);
    if (tab === 'unread') return isUnread(item);
    return true;
  });

  const sorted = [...filtered].sort((a, b) => {
    if (sort === 'added') return b.addedAt.localeCompare(a.addedAt);
    return b.lastReadAt.localeCompare(a.lastReadAt);
  });

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = sorted.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  const remove = async (item: BookshelfItem) => {
    if (confirmId !== item.novelId) {
      setConfirmId(item.novelId);
      return;
    }
    setRemovingId(item.novelId);
    try {
      await api.removeFromShelf(item.novelId);
      setConfirmId(null);
      toast('已移出书架');
      await queryClient.invalidateQueries({ queryKey: ['bookshelf'] });
    } catch (error) {
      toast(errorMessage(error), 'error');
    } finally {
      setRemovingId(null);
    }
  };

  if (status === 'loading') {
    return (
      <div className="mx-auto max-w-[1240px] px-6 py-12 lg:px-10">
        <Skeleton className="h-4 w-28" />
        <Skeleton className="mt-4 h-10 w-56" />
        <div className="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }, (_, index) => (
            <div key={index} className="rounded-[10px] border border-line p-4">
              <div className="flex gap-4">
                <Skeleton className="aspect-[3/4] w-20 shrink-0" />
                <div className="flex-1 space-y-3">
                  <Skeleton className="h-5 w-2/3" />
                  <Skeleton className="h-3 w-1/3" />
                  <Skeleton className="h-2 w-full" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="mx-auto max-w-[900px] px-6 py-20 lg:px-10">
        <EmptyState
          icon={<Lock size={26} strokeWidth={1.2} />}
          title="登录后才能使用书架"
          description="书架、阅读进度和评分都存在你的账号下；注册只需要一个用户名和密码。"
          action={
            <ButtonLink to="/login" state={{ from: '/bookshelf' }}>
              去登录
            </ButtonLink>
          }
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-[1240px] px-6 pb-24 pt-12 lg:px-10 lg:pt-16">
      <header className="flex flex-wrap items-end justify-between gap-5 border-b border-line pb-8">
        <div>
          <p className="text-[10.5px] tracking-[0.28em] text-accent">MY LIBRARY</p>
          <h1 className="mt-3 font-serif text-[34px] font-semibold text-ink sm:text-[40px]">
            我的书架
          </h1>
          <p className="mt-2 text-[12.5px] text-muted">
            共 {all.length} 本 · 在读 {reading.length} · 已读完 {finished.length}
            {unread.length > 0 ? ` · 未开始 ${unread.length}` : ''}
          </p>
        </div>
        <ButtonLink to="/browse" variant="secondary" size="md">
          去书库找书
        </ButtonLink>
      </header>

      <div className="mt-8 flex flex-wrap items-center justify-between gap-5">
        <UnderlineTabs
          options={[
            { value: 'all', label: '全部', hint: String(all.length) },
            { value: 'reading', label: '在读', hint: String(reading.length) },
            { value: 'finished', label: '已读完', hint: String(finished.length) },
            { value: 'unread', label: '未开始', hint: String(unread.length) },
          ]}
          value={tab}
          onChange={(value) => {
            setTab(value);
            setPage(1);
            setConfirmId(null);
          }}
        />
        <Segmented
          options={SORTS}
          value={sort}
          onChange={(value) => {
            setSort(value);
            setPage(1);
          }}
          size="sm"
        />
      </div>

      {shelfQuery.isError ? (
        <LoadError
          className="mt-10"
          error={shelfQuery.error}
          onRetry={() => void shelfQuery.refetch()}
        />
      ) : shelfQuery.isLoading ? (
        <div className="mt-8 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }, (_, index) => (
            <div key={index} className="rounded-[10px] border border-line p-4">
              <div className="flex gap-4">
                <Skeleton className="aspect-[3/4] w-20 shrink-0" />
                <div className="flex-1 space-y-3">
                  <Skeleton className="h-5 w-2/3" />
                  <Skeleton className="h-3 w-1/3" />
                  <Skeleton className="h-2 w-full" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : all.length === 0 ? (
        <EmptyState
          className="mt-10"
          icon={<Library size={26} strokeWidth={1.2} />}
          title="书架还是空的"
          description="看到想读的书，点一下「加入书架」；读到哪、还剩多少章，都会记在这里。"
          action={<ButtonLink to="/browse">去书库逛逛</ButtonLink>}
        />
      ) : sorted.length === 0 ? (
        <EmptyState
          className="mt-10"
          icon={<BookOpen size={26} strokeWidth={1.2} />}
          title="这个筛选下还没有书"
          description="换一个状态看看，或者回书库再加几本。"
          action={
            <Button variant="secondary" size="sm" onClick={() => setTab('all')}>
              查看全部
            </Button>
          }
        />
      ) : (
        <>
          <div className="mt-8 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {pageItems.map((item) => (
              <ShelfCard
                key={item.novelId}
                item={item}
                categoryName={nameOf(item.categoryId)}
                confirming={confirmId === item.novelId}
                removing={removingId === item.novelId}
                onRemove={() => void remove(item)}
              />
            ))}
          </div>
          <Pagination
            className="mt-12"
            page={currentPage}
            total={sorted.length}
            size={PAGE_SIZE}
            onChange={setPage}
          />
        </>
      )}
    </div>
  );
}

function ShelfCard({
  item,
  categoryName,
  confirming,
  removing,
  onRemove,
}: {
  item: BookshelfItem;
  categoryName: string;
  confirming: boolean;
  removing: boolean;
  onRemove: () => void;
}) {
  const finished = isFinished(item);
  const unread = isUnread(item);
  const percent = readingPercent(item.lastChapterSeq, item.totalChapters);
  const startSeq = Math.max(1, item.lastChapterSeq);

  return (
    <article className="flex flex-col rounded-[10px] border border-line bg-surface/60 p-4 transition-colors hover:border-line-strong">
      <div className="flex gap-4">
        <Link to={`/novel/${item.novelId}`} className="w-20 shrink-0">
          <NovelCover
            novel={{
              title: item.title,
              authorName: item.authorName,
              coverKey: item.coverKey,
              categoryId: item.categoryId,
            }}
          />
        </Link>

        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <Link to={`/novel/${item.novelId}`} className="min-w-0">
              <h2 className="truncate font-serif text-[17px] text-ink transition-colors hover:text-accent">
                {item.title}
              </h2>
            </Link>
            {item.hasUpdate ? (
              <span className="shrink-0 rounded-full bg-accent-soft px-2 py-0.5 text-[10px] text-accent">
                有新章
              </span>
            ) : null}
          </div>
          <p className="mt-1 truncate text-[11.5px] text-muted">
            {item.authorName} · {categoryName}
          </p>

          <div className="mt-3 h-[3px] w-full overflow-hidden rounded-full bg-line">
            <div
              className={cn('h-full', finished ? 'bg-gold' : 'bg-accent')}
              style={{ width: `${finished ? 100 : percent}%` }}
            />
          </div>
          <p className="mt-1.5 text-[11.5px] text-muted">
            {unread
              ? `还没开始读 · 共 ${item.totalChapters} 章`
              : finished
                ? `已读完 · 共 ${item.totalChapters} 章`
                : `读到第 ${item.lastChapterSeq} 章 · 共 ${item.totalChapters} 章`}
          </p>
          <p className="mt-0.5 text-[10.5px] text-faint">
            {unread ? `加入于 ${formatRelativeTime(item.addedAt)}` : `上次阅读 ${formatRelativeTime(item.lastReadAt)}`}
          </p>
        </div>
      </div>

      <div className="mt-auto flex items-center gap-2 pt-4">
        <ButtonLink
          to={`/novel/${item.novelId}/chapters/${startSeq}`}
          size="sm"
          variant={finished ? 'secondary' : 'primary'}
        >
          {unread ? '开始阅读' : finished ? '再看一遍' : '继续阅读'}
        </ButtonLink>
        <Button
          variant="ghost"
          size="sm"
          onClick={onRemove}
          disabled={removing}
          className={cn(confirming && 'text-danger hover:text-danger')}
        >
          <Trash2 size={13} />
          {removing ? '移出中…' : confirming ? '确认移出' : '移出'}
        </Button>
      </div>
    </article>
  );
}
