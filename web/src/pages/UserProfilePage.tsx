import { Link, useParams } from 'react-router-dom';
import type { ReactNode } from 'react';
import { ArrowLeft, BookOpen, Lock, MessageSquare, PenLine, ThumbsUp } from 'lucide-react';
import { useUserDetail } from '@/api/queries';
import { useAuth } from '@/features/auth/AuthProvider';
import { Avatar } from '@/components/ui/Avatar';
import { ButtonLink } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError } from '@/components/ui/LoadError';
import { Skeleton } from '@/components/ui/Skeleton';
import { formatCount, formatDate } from '@/lib/format';
import { NotFoundPage } from './NotFoundPage';

export function UserProfilePage() {
  const { id } = useParams();
  const userId = Number(id);
  const { user } = useAuth();
  const detailQuery = useUserDetail(userId);

  if (!Number.isFinite(userId) || userId <= 0) return <NotFoundPage />;

  if (detailQuery.isLoading) {
    return (
      <div className="mx-auto max-w-[900px] px-6 py-12 lg:px-10">
        <Skeleton className="h-4 w-24" />
        <div className="mt-8 flex items-center gap-5">
          <Skeleton className="h-[76px] w-[76px] rounded-full" />
          <div className="space-y-3">
            <Skeleton className="h-9 w-44" />
            <Skeleton className="h-4 w-28" />
          </div>
        </div>
        <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-24 rounded-[8px]" />
          ))}
        </div>
      </div>
    );
  }

  if (detailQuery.isError) {
    const code = (detailQuery.error as { code?: number })?.code;
    if (code === 10102) return <NotFoundPage />;
    return (
      <div className="mx-auto max-w-[900px] px-6 py-16 lg:px-10">
        <LoadError error={detailQuery.error} onRetry={() => void detailQuery.refetch()} />
      </div>
    );
  }

  const detail = detailQuery.data;
  if (!detail) return <NotFoundPage />;

  const profile = detail.user;
  const stat = detail.stat;
  const isMe = user?.id === profile.id;

  return (
    <div className="mx-auto max-w-[900px] px-6 pb-24 pt-10 lg:px-10 lg:pt-14">
      <Link
        to="/community"
        className="inline-flex items-center gap-1.5 text-[12px] text-muted transition-colors hover:text-ink"
      >
        <ArrowLeft size={13} />
        返回社区
      </Link>

      <header className="mt-7 flex flex-wrap items-start justify-between gap-6 border-b border-line pb-9">
        <div className="flex items-start gap-5">
          <Avatar name={profile.nickname} avatarKey={profile.avatarKey} size={76} />
          <div className="min-w-0">
            <p className="text-[10.5px] tracking-[0.28em] text-accent">READER</p>
            <h1 className="mt-2 font-serif text-[32px] font-semibold text-ink sm:text-[38px]">
              {profile.nickname}
            </h1>
            <p className="mt-1 text-[12.5px] text-muted">
              @{profile.username}
              <span className="mx-2 text-line-strong">·</span>
              加入于 {formatDate(profile.createdAt)}
            </p>
            <p className="mt-3 max-w-[32rem] text-[13px] leading-[1.9] text-ink-soft">
              {profile.bio?.trim() ? profile.bio : 'TA 还没有写简介。'}
            </p>
          </div>
        </div>

        {isMe ? (
          <ButtonLink to="/me" variant="secondary" size="md">
            编辑我的资料
          </ButtonLink>
        ) : null}
      </header>

      <section className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-4" aria-label="公开统计">
        <StatCard label="书架" value={formatCount(stat.bookshelfCount)} icon={<BookOpen size={14} />} />
        <StatCard
          label="书评"
          value={formatCount(stat.reviewCount)}
          icon={<MessageSquare size={14} />}
        />
        <StatCard label="帖子" value={formatCount(stat.postCount)} icon={<PenLine size={14} />} />
        <StatCard label="获赞" value={formatCount(stat.likeReceived)} icon={<ThumbsUp size={14} />} />
      </section>

      <section className="mt-12 border-t border-line/70 pt-10">
        <div className="flex items-center gap-2 text-[10.5px] tracking-[0.24em] text-faint">
          <Lock size={12} />
          公开范围
        </div>
        <p className="mt-3 max-w-xl text-[13px] leading-[1.9] text-muted">
          这里只展示公开资料与统计。书架明细、书评与帖子列表默认不公开；想找 TA 的讨论，
          可以去社区里搜同名的帖子。
        </p>
        {isMe ? (
          <EmptyState
            className="mt-6"
            title="这是你自己的公开主页"
            description="别人看到的就是这一页；书架、资料编辑和正在读的书在个人主页里。"
            action={<ButtonLink to="/me">回到个人主页</ButtonLink>}
          />
        ) : (
          <div className="mt-6">
            <ButtonLink to="/community" variant="secondary" size="sm">
              去社区看看
            </ButtonLink>
          </div>
        )}
      </section>
    </div>
  );
}

function StatCard({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon: ReactNode;
}) {
  return (
    <div className="rounded-[8px] border border-line bg-surface/50 px-4 py-3">
      <p className="flex items-center gap-1.5 text-[10.5px] tracking-[0.16em] text-faint">
        {icon}
        {label}
      </p>
      <p className="mt-1 font-serif text-[26px] leading-none text-ink">{value}</p>
    </div>
  );
}
