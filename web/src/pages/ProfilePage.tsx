import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  BookOpen,
  Edit3,
  Lock,
  LogOut,
  MessageSquare,
  PenLine,
  ThumbsUp,
  X,
} from 'lucide-react';
import type { User } from '@/api/types';
import { api } from '@/api/endpoints';
import {
  qk,
  useBookshelfPage,
  useUserDetail,
  useUserPosts,
  useUserReviews,
} from '@/api/queries';
import { useAuth } from '@/features/auth/AuthProvider';
import { useToast } from '@/components/ui/Toast';
import { Avatar } from '@/components/ui/Avatar';
import { Button, ButtonLink } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError, errorMessage } from '@/components/ui/LoadError';
import { Skeleton } from '@/components/ui/Skeleton';
import { Pagination } from '@/components/ui/Pagination';
import { UnderlineTabs } from '@/components/ui/Segmented';
import { NovelCover } from '@/components/book/NovelCover';
import { formatCount, formatDate, formatRelativeTime, genderLabel, readingPercent } from '@/lib/format';

type ContentTab = 'reviews' | 'posts';

export function ProfilePage() {
  const { user, status, refreshUser, logout } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const detailQuery = useUserDetail(user?.id ?? 0);
  const shelfQuery = useBookshelfPage(1, 50);
  const [editing, setEditing] = useState(false);
  const [contentTab, setContentTab] = useState<ContentTab>('reviews');
  const [contentPage, setContentPage] = useState(1);
  const reviewsPage = useUserReviews(user?.id ?? 0, contentPage, 6);
  const postsPage = useUserPosts(user?.id ?? 0, contentPage, 6);

  if (status === 'loading') {
    return (
      <div className="mx-auto max-w-[1040px] px-6 py-12 lg:px-10">
        <div className="flex items-center gap-5 border-b border-line pb-8">
          <Skeleton className="h-[76px] w-[76px] rounded-full" />
          <div className="space-y-3">
            <Skeleton className="h-9 w-48" />
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-4 w-64" />
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

  if (!user) {
    return (
      <div className="mx-auto max-w-[900px] px-6 py-20 lg:px-10">
        <EmptyState
          icon={<Lock size={26} strokeWidth={1.2} />}
          title="登录后查看个人主页"
          description="书架、评分和阅读进度都归属于账号；登录后这里会汇总你的阅读状态。"
          action={
            <ButtonLink to="/login" state={{ from: '/me' }}>
              去登录
            </ButtonLink>
          }
        />
      </div>
    );
  }

  const profile = detailQuery.data?.user ?? user;
  const stats = detailQuery.data?.stat;
  const shelf = shelfQuery.data?.list ?? [];
  const reading = shelf.filter(
    (item) => item.lastChapterSeq > 0 && item.lastChapterSeq < item.totalChapters,
  );
  const readingPreview = reading.slice(0, 3);
  const shelfPreview = shelf.slice(0, 6);

  const handleLogout = async () => {
    await logout();
    toast('已退出登录');
    navigate('/');
  };

  const handleSaved = async (updated: User) => {
    setEditing(false);
    await refreshUser();
    await queryClient.invalidateQueries({ queryKey: qk.userDetail(updated.id) });
    toast('资料已更新');
  };

  return (
    <div className="mx-auto max-w-[1040px] px-6 pb-24 pt-12 lg:px-10 lg:pt-16">
      <header className="flex flex-wrap items-start justify-between gap-6 border-b border-line pb-9">
        <div className="flex items-start gap-5">
          <Avatar name={profile.nickname} avatarKey={profile.avatarKey} size={76} />
          <div className="min-w-0">
            <p className="text-[10.5px] tracking-[0.28em] text-accent">PROFILE</p>
            <h1 className="mt-2 font-serif text-[34px] font-semibold text-ink sm:text-[40px]">
              {profile.nickname}
            </h1>
            <p className="mt-1 text-[12.5px] text-muted">
              @{profile.username}
              <span className="mx-2 text-line-strong">·</span>
              加入于 {formatDate(profile.createdAt)}
            </p>
            <p className="mt-3 max-w-[34rem] text-[13px] leading-[1.9] text-ink-soft">
              {profile.bio?.trim() ? profile.bio : '还没有写简介。'}
            </p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => setEditing(true)}>
            <Edit3 size={14} />
            编辑资料
          </Button>
          <ButtonLink to="/bookshelf">
            我的书架
          </ButtonLink>
        </div>
      </header>

      {detailQuery.isError ? (
        <LoadError
          className="mt-8"
          error={detailQuery.error}
          onRetry={() => void detailQuery.refetch()}
        />
      ) : (
        <section className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-4" aria-label="阅读统计">
          <StatCard label="书架" value={stats ? formatCount(stats.bookshelfCount) : '—'} />
          <StatCard
            label="书评"
            value={stats ? formatCount(stats.reviewCount) : '—'}
            icon={<MessageSquare size={14} />}
          />
          <StatCard
            label="帖子"
            value={stats ? formatCount(stats.postCount) : '—'}
            icon={<PenLine size={14} />}
          />
          <StatCard
            label="获赞"
            value={stats ? formatCount(stats.likeReceived) : '—'}
            icon={<ThumbsUp size={14} />}
          />
        </section>
      )}

      <section className="mt-12 border-t border-line/70 pt-10">
        <div className="flex items-end justify-between gap-4">
          <div>
            <p className="text-[10.5px] tracking-[0.28em] text-accent">READING</p>
            <h2 className="mt-2 font-serif text-[24px] font-semibold text-ink">正在读</h2>
          </div>
          <Link
            to="/bookshelf"
            className="text-[12px] text-muted transition-colors hover:text-accent"
          >
            全部书架
          </Link>
        </div>

        {shelfQuery.isError ? (
          <LoadError
            className="mt-6"
            error={shelfQuery.error}
            onRetry={() => void shelfQuery.refetch()}
          />
        ) : shelfQuery.isLoading ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-28 rounded-[10px]" />
            ))}
          </div>
        ) : readingPreview.length === 0 ? (
          <p className="mt-5 text-[13px] leading-relaxed text-muted">
            还没有正在读的书。
            <Link to="/browse" className="ml-1 text-accent hover:text-accent-deep">
              去书库挑一本
            </Link>
          </p>
        ) : (
          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            {readingPreview.map((item) => (
              <Link
                key={item.novelId}
                to={`/novel/${item.novelId}/chapters/${Math.max(1, item.lastChapterSeq)}`}
                className="group flex gap-3 rounded-[10px] border border-line bg-surface/60 p-3 transition-colors hover:border-line-strong"
              >
                <NovelCover
                  novel={{
                    title: item.title,
                    authorName: item.authorName,
                    coverKey: item.coverKey,
                    categoryId: item.categoryId,
                  }}
                  className="w-12 shrink-0"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-serif text-[14.5px] text-ink transition-colors group-hover:text-accent">
                    {item.title}
                  </p>
                  <p className="mt-0.5 text-[11px] text-muted">
                    第 {item.lastChapterSeq} / {item.totalChapters} 章
                  </p>
                  <div className="mt-2.5 h-[3px] w-full overflow-hidden rounded-full bg-line">
                    <div
                      className="h-full bg-accent"
                      style={{
                        width: `${readingPercent(item.lastChapterSeq, item.totalChapters)}%`,
                      }}
                    />
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="mt-12 border-t border-line/70 pt-10">
        <div className="flex items-end justify-between gap-4">
          <div>
            <p className="text-[10.5px] tracking-[0.28em] text-accent">SHELF</p>
            <h2 className="mt-2 font-serif text-[24px] font-semibold text-ink">我的书架</h2>
          </div>
          <Link
            to="/bookshelf"
            className="text-[12px] text-muted transition-colors hover:text-accent"
          >
            查看全部
          </Link>
        </div>

        {shelfPreview.length === 0 ? (
          <div className="mt-6">
            <EmptyState
              icon={<BookOpen size={24} strokeWidth={1.2} />}
              title="书架还是空的"
              description="看到想读的书，加入书架后就会出现在这里。"
              action={<ButtonLink to="/browse" variant="secondary" size="sm">去书库逛逛</ButtonLink>}
            />
          </div>
        ) : (
          <div className="mt-6 grid grid-cols-3 gap-x-5 gap-y-7 sm:grid-cols-6">
            {shelfPreview.map((item) => (
              <Link key={item.novelId} to={`/novel/${item.novelId}`} className="group block">
                <NovelCover
                  novel={{
                    title: item.title,
                    authorName: item.authorName,
                    coverKey: item.coverKey,
                    categoryId: item.categoryId,
                  }}
                />
                <p className="mt-2 truncate text-[12.5px] text-ink-soft transition-colors group-hover:text-accent">
                  {item.title}
                </p>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="mt-12 border-t border-line/70 pt-10">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-[10.5px] tracking-[0.28em] text-accent">MY WRITING</p>
            <h2 className="mt-2 font-serif text-[24px] font-semibold text-ink">我的内容</h2>
          </div>
          <p className="text-[11.5px] text-faint">
            书评 {stats ? formatCount(stats.reviewCount) : '—'} · 帖子{' '}
            {stats ? formatCount(stats.postCount) : '—'}
          </p>
        </div>

        <UnderlineTabs
          className="mt-5"
          options={[
            {
              value: 'reviews',
              label: '我的书评',
              hint: stats ? `(${stats.reviewCount})` : '',
            },
            {
              value: 'posts',
              label: '我的帖子',
              hint: stats ? `(${stats.postCount})` : '',
            },
          ]}
          value={contentTab}
          onChange={(value) => {
            setContentTab(value);
            setContentPage(1);
          }}
        />

        {contentTab === 'reviews' ? (
          <MyReviews
            query={reviewsPage}
            page={contentPage}
            onPage={setContentPage}
          />
        ) : (
          <MyPosts
            query={postsPage}
            page={contentPage}
            onPage={setContentPage}
          />
        )}
      </section>

      <section className="mt-12 border-t border-line/70 pt-10">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-[10.5px] tracking-[0.28em] text-accent">ACCOUNT</p>
            <h2 className="mt-2 font-serif text-[24px] font-semibold text-ink">账号</h2>
          </div>
          <Button variant="secondary" size="sm" onClick={() => void handleLogout()}>
            <LogOut size={13} />
            退出登录
          </Button>
        </div>
        <dl className="mt-6 grid gap-x-8 gap-y-4 border-t border-line/70 pt-5 text-[13px] sm:grid-cols-3">
          <div>
            <dt className="text-[10.5px] tracking-[0.16em] text-faint">用户名</dt>
            <dd className="mt-1 text-ink-soft">@{profile.username}</dd>
          </div>
          <div>
            <dt className="text-[10.5px] tracking-[0.16em] text-faint">用户 ID</dt>
            <dd className="mt-1 tabular-nums text-ink-soft">{profile.id}</dd>
          </div>
          <div>
            <dt className="text-[10.5px] tracking-[0.16em] text-faint">加入时间</dt>
            <dd className="mt-1 text-ink-soft">{formatDate(profile.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-[10.5px] tracking-[0.16em] text-faint">性别</dt>
            <dd className="mt-1 text-ink-soft">{genderLabel(profile.gender)}</dd>
          </div>
          <div>
            <dt className="text-[10.5px] tracking-[0.16em] text-faint">生日</dt>
            <dd className="mt-1 text-ink-soft">
              {profile.birthday ? formatDate(profile.birthday) : '未设置'}
            </dd>
          </div>
        </dl>
      </section>

      {editing ? (
        <ProfileEditDialog
          profile={profile}
          onClose={() => setEditing(false)}
          onSaved={handleSaved}
        />
      ) : null}
    </div>
  );
}

function MyReviews({
  query,
  page,
  onPage,
}: {
  query: ReturnType<typeof useUserReviews>;
  page: number;
  onPage: (page: number) => void;
}) {
  if (query.isError) {
    return <LoadError className="mt-5" error={query.error} onRetry={() => void query.refetch()} />;
  }
  if (query.isLoading) {
    return (
      <div className="mt-5 space-y-4">
        {Array.from({ length: 3 }, (_, index) => (
          <div key={index} className="space-y-2 border-b border-line/60 py-4">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-4 w-full" />
          </div>
        ))}
      </div>
    );
  }
  const items = query.data?.list ?? [];
  if (items.length === 0) {
    return (
      <EmptyState
        className="mt-5"
        title="还没有写过书评"
        description="读完一本书，把最想说的那句话留下来。"
        action={<ButtonLink to="/browse" variant="secondary" size="sm">去书库逛逛</ButtonLink>}
      />
    );
  }
  return (
    <>
      <div className="mt-2 divide-y divide-line/60">
        {items.map((item) => (
          <Link
            key={item.id}
            to={`/novel/${item.novelId}`}
            className="group block py-4 first:pt-3"
          >
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <p className="font-serif text-[15px] text-ink transition-colors group-hover:text-accent">
                《{item.novelTitle}》
              </p>
              {item.score ? (
                <span className="text-[11px] text-gold">{item.score} 星</span>
              ) : null}
            </div>
            <p className="mt-1.5 line-clamp-2 text-[12.5px] leading-[1.85] text-muted">
              {item.content}
            </p>
            <p className="mt-1.5 text-[10.5px] text-faint">
              {formatRelativeTime(item.createdAt)} · {formatCount(item.likeCount)} 赞 ·{' '}
              {formatCount(item.replyCount)} 回复
            </p>
          </Link>
        ))}
      </div>
      <Pagination
        className="mt-6"
        page={query.data?.page ?? page}
        total={query.data?.total ?? 0}
        size={6}
        onChange={onPage}
      />
    </>
  );
}

function MyPosts({
  query,
  page,
  onPage,
}: {
  query: ReturnType<typeof useUserPosts>;
  page: number;
  onPage: (page: number) => void;
}) {
  if (query.isError) {
    return <LoadError className="mt-5" error={query.error} onRetry={() => void query.refetch()} />;
  }
  if (query.isLoading) {
    return (
      <div className="mt-5 space-y-4">
        {Array.from({ length: 3 }, (_, index) => (
          <div key={index} className="space-y-2 border-b border-line/60 py-4">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-4 w-full" />
          </div>
        ))}
      </div>
    );
  }
  const items = query.data?.list ?? [];
  if (items.length === 0) {
    return (
      <EmptyState
        className="mt-5"
        title="还没有发过帖子"
        description="社区里可以聊书，也可以只写一段读完的感受。"
        action={<ButtonLink to="/posts/new" variant="secondary" size="sm">写第一条帖子</ButtonLink>}
      />
    );
  }
  return (
    <>
      <div className="mt-2 divide-y divide-line/60">
        {items.map((item) => (
          <Link key={item.id} to={`/posts/${item.id}`} className="group block py-4 first:pt-3">
            <p className="font-serif text-[15px] text-ink transition-colors group-hover:text-accent">
              {item.title}
            </p>
            <p className="mt-1.5 line-clamp-2 text-[12.5px] leading-[1.85] text-muted">
              {item.summary}
            </p>
            <p className="mt-1.5 text-[10.5px] text-faint">
              {formatRelativeTime(item.createdAt)} · {formatCount(item.likeCount)} 赞 ·{' '}
              {formatCount(item.commentCount)} 评论
            </p>
          </Link>
        ))}
      </div>
      <Pagination
        className="mt-6"
        page={query.data?.page ?? page}
        total={query.data?.total ?? 0}
        size={6}
        onChange={onPage}
      />
    </>
  );
}

function StatCard({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon?: ReactNode;
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

function ProfileEditDialog({
  profile,
  onClose,
  onSaved,
}: {
  profile: User;
  onClose: () => void;
  onSaved: (user: User) => void | Promise<void>;
}) {
  const [nickname, setNickname] = useState(profile.nickname);
  const [bio, setBio] = useState(profile.bio ?? '');
  const [gender, setGender] = useState(profile.gender ?? 0);
  const [birthday, setBirthday] = useState(profile.birthday ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const today = new Date().toISOString().slice(0, 10);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const nextNickname = nickname.trim();
    if (nextNickname.length < 1 || nextNickname.length > 20) {
      setError('昵称长度 1–20 个字符');
      return;
    }
    if (bio.length > 255) {
      setError('个性签名最长 255 个字符');
      return;
    }
    if (birthday && birthday >= today) {
      setError('生日必须是过去的日期');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await api.updateProfile({
        nickname: nextNickname,
        bio: bio.trim(),
        gender,
        birthday: birthday || undefined,
      });
      await onSaved(updated);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/35 px-4 backdrop-blur-sm"
      onMouseDown={onClose}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="profile-edit-title"
        className="w-full max-w-[430px] rounded-[10px] border border-line bg-surface p-6 shadow-pop"
        onMouseDown={(event) => event.stopPropagation()}
        onSubmit={submit}
      >
        <div className="flex items-center justify-between">
          <h2 id="profile-edit-title" className="font-serif text-[20px] text-ink">
            编辑资料
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="text-faint transition-colors hover:text-ink"
          >
            <X size={16} />
          </button>
        </div>

        <label className="mt-6 block">
          <span className="text-[11.5px] text-muted">昵称</span>
          <input
            aria-label="昵称"
            value={nickname}
            onChange={(event) => setNickname(event.target.value)}
            maxLength={20}
            className="mt-1.5 h-10 w-full rounded-[6px] border border-line bg-paper/60 px-3 text-[14px] text-ink focus:border-line-strong focus:outline-none"
          />
        </label>

        <label className="mt-4 block">
          <span className="flex items-center justify-between text-[11.5px] text-muted">
            个性签名
            <span className="text-[10.5px] text-faint">{bio.length} / 255</span>
          </span>
          <textarea
            aria-label="个性签名"
            value={bio}
            onChange={(event) => setBio(event.target.value)}
            rows={4}
            maxLength={255}
            placeholder="一句话介绍自己，或者最近在读什么"
            className="mt-1.5 w-full resize-none rounded-[6px] border border-line bg-paper/60 px-3 py-2.5 text-[13.5px] leading-relaxed text-ink placeholder:text-faint focus:border-line-strong focus:outline-none"
          />
        </label>

        <div className="mt-4 grid grid-cols-2 gap-3">
          <label className="block">
            <span className="text-[11.5px] text-muted">性别</span>
            <select
              aria-label="性别"
              value={String(gender)}
              onChange={(event) => setGender(Number(event.target.value))}
              className="mt-1.5 h-10 w-full rounded-[6px] border border-line bg-paper/60 px-2 text-[13px] text-ink focus:border-line-strong focus:outline-none"
            >
              <option value="0">未设置</option>
              <option value="1">男</option>
              <option value="2">女</option>
            </select>
          </label>
          <label className="block">
            <span className="text-[11.5px] text-muted">生日</span>
            <input
              aria-label="生日"
              type="date"
              value={birthday}
              max={today}
              onChange={(event) => setBirthday(event.target.value)}
              className="mt-1.5 h-10 w-full rounded-[6px] border border-line bg-paper/60 px-2 text-[13px] text-ink focus:border-line-strong focus:outline-none"
            />
          </label>
        </div>

        {error ? (
          <p className="mt-3 rounded-[6px] border border-danger/25 bg-danger-soft px-3 py-2 text-[12px] text-danger">
            {error}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
            取消
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? '保存中…' : '保存'}
          </Button>
        </div>
      </form>
    </div>
  );
}
