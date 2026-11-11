import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { MessagesSquare, PenLine, X } from 'lucide-react';
import type { PostView } from '@/api/types';
import { api } from '@/api/endpoints';
import { usePosts } from '@/api/queries';
import { useAuth } from '@/features/auth/AuthProvider';
import { useToast } from '@/components/ui/Toast';
import { ButtonLink } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError, errorMessage } from '@/components/ui/LoadError';
import { Pagination } from '@/components/ui/Pagination';
import { Segmented } from '@/components/ui/Segmented';
import { Skeleton } from '@/components/ui/Skeleton';
import { PostCard } from '@/components/community/PostCard';

type PostSort = 'hot' | 'latest';

const SORTS: { value: PostSort; label: string }[] = [
  { value: 'hot', label: '最热' },
  { value: 'latest', label: '最新' },
];

const PAGE_SIZE = 10;

export function CommunityPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const rawSort = params.get('sort');
  const sort: PostSort = rawSort === 'latest' ? 'latest' : 'hot';
  const page = Math.max(1, Number(params.get('page') ?? 1) || 1);
  const novelId = Number(params.get('novelId') ?? 0) || undefined;

  const [likes, setLikes] = useState<Record<number, boolean>>({});
  const { data, isLoading, isError, error, refetch } = usePosts({
    sort,
    novelId,
    page,
    size: PAGE_SIZE,
  });

  const posts = data?.list ?? [];

  const update = (next: Record<string, string | number | undefined>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([key, value]) => {
      if (value === undefined || value === '' || value === 0) merged.delete(key);
      else merged.set(key, String(value));
    });
    if (!('page' in next)) merged.delete('page');
    setParams(merged, { replace: true });
  };

  const toggleLike = async (post: PostView) => {
    if (!user) {
      toast('登录后才能点赞', 'error');
      navigate('/login', { state: { from: location.pathname + location.search } });
      return;
    }
    const current = likes[post.id] ?? post.liked;
    setLikes((all) => ({ ...all, [post.id]: !current }));
    try {
      if (current) await api.unlikePost(post.id);
      else await api.likePost(post.id);
      await queryClient.invalidateQueries({ queryKey: ['posts'] });
    } catch (caught) {
      setLikes((all) => ({ ...all, [post.id]: current }));
      toast(errorMessage(caught), 'error');
    }
  };

  return (
    <div className="mx-auto max-w-[1040px] px-6 pb-24 pt-12 lg:px-10 lg:pt-16">
      <header className="flex flex-wrap items-end justify-between gap-5 border-b border-line pb-8">
        <div>
          <p className="text-[10.5px] tracking-[0.28em] text-accent">READERS' NOTES</p>
          <h1 className="mt-3 font-serif text-[34px] font-semibold text-ink sm:text-[40px]">
            社区
          </h1>
          <p className="mt-2 max-w-xl text-[12.5px] leading-relaxed text-muted">
            读完想说的话、想找人讨论的段落，都可以放在这里。帖子可以引用最多五本书。
          </p>
        </div>
        <ButtonLink to="/posts/new" size="md">
          <PenLine size={14} />
          写帖子
        </ButtonLink>
      </header>

      {novelId ? (
        <div className="mt-5 flex items-center justify-between gap-4 rounded-[6px] border border-line bg-paper-deep/60 px-4 py-2.5 text-[12px] text-muted">
          <span>只显示引用了指定小说的帖子</span>
          <button
            type="button"
            onClick={() => update({ novelId: undefined })}
            className="inline-flex items-center gap-1 text-accent transition-colors hover:text-accent-deep"
          >
            <X size={12} />
            清除筛选
          </button>
        </div>
      ) : null}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
        <Segmented
          options={SORTS}
          value={sort}
          onChange={(value) => update({ sort: value })}
          size="sm"
        />
        <p className="text-[11.5px] text-faint">{data ? `共 ${data.total} 条讨论` : ''}</p>
      </div>

      {isError ? (
        <LoadError className="mt-8" error={error} onRetry={() => void refetch()} />
      ) : isLoading ? (
        <div className="mt-6 space-y-4">
          {Array.from({ length: 4 }, (_, index) => (
            <div key={index} className="rounded-[10px] border border-line p-5">
              <div className="flex items-center gap-3">
                <Skeleton className="h-8 w-8 rounded-full" />
                <Skeleton className="h-4 w-24" />
              </div>
              <Skeleton className="mt-4 h-6 w-3/5" />
              <Skeleton className="mt-3 h-4 w-full" />
              <Skeleton className="mt-2 h-4 w-2/3" />
            </div>
          ))}
        </div>
      ) : posts.length === 0 ? (
        <EmptyState
          className="mt-8"
          icon={<MessagesSquare size={26} strokeWidth={1.2} />}
          title={novelId ? '还没有人讨论这本书' : '社区还很安静'}
          description={
            novelId
              ? '可以从这本书的书评开始，也可以发一条引用它的帖子。'
              : '第一条帖子往往决定了一个社区的气质。写点什么吧。'
          }
          action={<ButtonLink to="/posts/new">写第一条帖子</ButtonLink>}
        />
      ) : (
        <>
          <div className="mt-6 space-y-4">
            {posts.map((post) => {
              const liked = likes[post.id] ?? post.liked;
              const delta =
                liked === post.liked ? 0 : liked ? 1 : -1;
              return (
                <PostCard
                  key={post.id}
                  post={post}
                  liked={liked}
                  likeCount={post.likeCount + delta}
                  onLike={() => void toggleLike(post)}
                />
              );
            })}
          </div>
          <Pagination
            className="mt-10"
            page={data?.page ?? page}
            total={data?.total ?? 0}
            size={PAGE_SIZE}
            onChange={(next) => update({ page: next })}
          />
        </>
      )}

      <div className="mt-10 flex items-center gap-2 text-[11.5px] text-faint">
        <MessagesSquare size={13} />
        最热按点赞、评论与时间衰减排序；最新按发帖时间倒序。
      </div>
    </div>
  );
}
