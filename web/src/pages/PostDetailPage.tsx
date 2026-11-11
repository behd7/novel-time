import { useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Eye,
  Heart,
  Lock,
  MessageCircle,
  Trash2,
} from 'lucide-react';
import type { PostComment, PostDetail, PostRef } from '@/api/types';
import { api } from '@/api/endpoints';
import { usePost, usePostComments } from '@/api/queries';
import { useAuth } from '@/features/auth/AuthProvider';
import { useToast } from '@/components/ui/Toast';
import { Avatar } from '@/components/ui/Avatar';
import { Button, ButtonLink } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError, errorMessage } from '@/components/ui/LoadError';
import { Skeleton } from '@/components/ui/Skeleton';
import { NovelCover } from '@/components/book/NovelCover';
import { formatCount, formatRating, formatRelativeTime } from '@/lib/format';
import { cn } from '@/lib/cn';
import { NotFoundPage } from './NotFoundPage';

export function PostDetailPage() {
  const { id } = useParams();
  const postId = Number(id);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { toast } = useToast();

  const postQuery = usePost(postId);
  const commentsQuery = usePostComments(postId, { page: 1, size: 50 });

  const [postLikeOverride, setPostLikeOverride] = useState<boolean | undefined>(undefined);
  const [commentLikes, setCommentLikes] = useState<Record<number, boolean>>({});
  const [replyTo, setReplyTo] = useState<PostComment | null>(null);
  const [confirmPostDelete, setConfirmPostDelete] = useState(false);
  const [confirmCommentId, setConfirmCommentId] = useState<number | null>(null);

  if (!Number.isFinite(postId) || postId <= 0) return <NotFoundPage />;

  if (postQuery.isLoading) {
    return (
      <div className="mx-auto max-w-[860px] px-6 py-12 lg:px-10">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="mt-8 h-10 w-3/4" />
        <Skeleton className="mt-4 h-4 w-40" />
        <Skeleton className="mt-8 h-40 w-full" />
      </div>
    );
  }

  if (postQuery.isError) {
    const code = (postQuery.error as { code?: number })?.code;
    if (code === 10501) return <NotFoundPage />;
    return (
      <div className="mx-auto max-w-[860px] px-6 py-16 lg:px-10">
        <LoadError error={postQuery.error} onRetry={() => void postQuery.refetch()} />
      </div>
    );
  }

  const post = postQuery.data;
  if (!post) return <NotFoundPage />;

  const comments = commentsQuery.data?.list ?? [];
  const roots = comments.filter((comment) => comment.parentCommentId == null);
  const repliesByRoot = new Map<number, PostComment[]>();
  comments
    .filter((comment) => comment.parentCommentId != null)
    .forEach((comment) => {
      const list = repliesByRoot.get(comment.rootId) ?? [];
      list.push(comment);
      repliesByRoot.set(comment.rootId, list);
    });

  const liked = postLikeOverride ?? post.liked;
  const likeCount = post.likeCount + (liked === post.liked ? 0 : liked ? 1 : -1);
  const isAuthor = user?.id === post.userId;

  const requireLogin = () => {
    toast('登录后才能操作', 'error');
    navigate('/login', { state: { from: location.pathname } });
  };

  const togglePostLike = async () => {
    if (!user) return requireLogin();
    const current = liked;
    setPostLikeOverride(!current);
    try {
      if (current) await api.unlikePost(post.id);
      else await api.likePost(post.id);
    } catch (error) {
      setPostLikeOverride(current);
      toast(errorMessage(error), 'error');
    }
  };

  const deletePost = async () => {
    if (!confirmPostDelete) {
      setConfirmPostDelete(true);
      return;
    }
    try {
      await api.deletePost(post.id);
      toast('帖子已删除');
      await queryClient.invalidateQueries({ queryKey: ['posts'] });
      navigate('/community');
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  };

  const toggleCommentLike = async (comment: PostComment) => {
    if (!user) return requireLogin();
    const current = commentLikes[comment.id] ?? comment.liked;
    setCommentLikes((all) => ({ ...all, [comment.id]: !current }));
    try {
      if (current) await api.unlikePostComment(comment.id);
      else await api.likePostComment(comment.id);
      await queryClient.invalidateQueries({ queryKey: ['post-comments', postId] });
    } catch (error) {
      setCommentLikes((all) => ({ ...all, [comment.id]: current }));
      toast(errorMessage(error), 'error');
    }
  };

  const deleteComment = async (comment: PostComment) => {
    if (confirmCommentId !== comment.id) {
      setConfirmCommentId(comment.id);
      return;
    }
    try {
      await api.deletePostComment(comment.id);
      setConfirmCommentId(null);
      toast('评论已删除');
      await queryClient.invalidateQueries({ queryKey: ['post-comments', postId] });
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  };

  return (
    <div className="mx-auto max-w-[860px] px-6 pb-24 pt-10 lg:px-10 lg:pt-14">
      <Link
        to="/community"
        className="inline-flex items-center gap-1.5 text-[12px] text-muted transition-colors hover:text-ink"
      >
        <ArrowLeft size={13} />
        返回社区
      </Link>

      <article className="mt-7 border-b border-line pb-8">
        <div className="flex items-center gap-3">
          <Link to={`/users/${post.userId}`} className="shrink-0">
            <Avatar name={post.nickname} avatarKey={post.avatarKey} size={36} />
          </Link>
          <div className="min-w-0">
            <Link
              to={`/users/${post.userId}`}
              className="block truncate text-[13px] text-ink-soft transition-colors hover:text-accent"
            >
              {post.nickname}
            </Link>
            <p className="text-[10.5px] text-faint">{formatRelativeTime(post.createdAt)}</p>
          </div>
          <div className="ml-auto flex items-center gap-2">
            {isAuthor ? (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => void deletePost()}
                className={cn(confirmPostDelete && 'text-danger hover:text-danger')}
              >
                <Trash2 size={13} />
                {confirmPostDelete ? '确认删除' : '删除'}
              </Button>
            ) : null}
            <button
              type="button"
              onClick={() => void togglePostLike()}
              className={cn(
                'inline-flex h-8 items-center gap-1.5 rounded-full border border-line px-3 text-[12px] transition-colors hover:border-line-strong',
                liked ? 'text-accent' : 'text-muted',
              )}
              aria-pressed={liked}
            >
              <Heart size={14} fill={liked ? 'currentColor' : 'none'} />
              {formatCount(likeCount)}
            </button>
          </div>
        </div>

        <h1 className="mt-6 font-serif text-[30px] font-semibold leading-[1.3] text-ink sm:text-[36px]">
          {post.title}
        </h1>

        <PostBody post={post} />

        <div className="mt-6 flex items-center gap-5 text-[11.5px] text-faint">
          <span className="inline-flex items-center gap-1.5">
            <Eye size={13} />
            {formatCount(post.viewCount)} 阅读
          </span>
          <span className="inline-flex items-center gap-1.5">
            <MessageCircle size={13} />
            {comments.length} 条评论
          </span>
        </div>
      </article>

      <section className="mt-8">
        <h2 className="font-serif text-[22px] text-ink">讨论</h2>

        {user ? (
          <CommentComposer
            postId={post.id}
            className="mt-4"
            onDone={() => setReplyTo(null)}
          />
        ) : (
          <div className="mt-4 flex items-center justify-between gap-4 rounded-[8px] border border-line bg-paper-deep/50 px-4 py-3">
            <p className="flex items-center gap-2 text-[12px] text-muted">
              <Lock size={13} />
              登录后参与讨论
            </p>
            <ButtonLink to="/login" state={{ from: location.pathname }} size="sm" variant="secondary">
              去登录
            </ButtonLink>
          </div>
        )}

        {commentsQuery.isError ? (
          <LoadError
            className="mt-6"
            error={commentsQuery.error}
            onRetry={() => void commentsQuery.refetch()}
          />
        ) : commentsQuery.isLoading ? (
          <div className="mt-6 space-y-4">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-20 w-full" />
            ))}
          </div>
        ) : comments.length === 0 ? (
          <EmptyState
            className="mt-6"
            title="还没有评论"
            description="第一条评论往往能把讨论打开。"
          />
        ) : (
          <div className="mt-4 divide-y divide-line/60">
            {roots.map((comment) => {
              const replies = repliesByRoot.get(comment.id) ?? [];
              return (
                <div key={comment.id} className="py-4">
                  <CommentRow
                    comment={comment}
                    postId={post.id}
                    currentUserId={user?.id}
                    liked={commentLikes[comment.id] ?? comment.liked}
                    onLike={() => void toggleCommentLike(comment)}
                    onReply={() => setReplyTo(comment)}
                    confirming={confirmCommentId === comment.id}
                    onDelete={() => void deleteComment(comment)}
                  />
                  {replyTo?.id === comment.id ? (
                    <CommentComposer
                      postId={post.id}
                      parentCommentId={comment.id}
                      parentNickname={comment.nickname}
                      className="ml-10 mt-3"
                      onDone={() => setReplyTo(null)}
                    />
                  ) : null}
                  {replies.length > 0 ? (
                    <div className="ml-10 mt-2 border-l border-line pl-4">
                      {replies.map((reply) => (
                        <div key={reply.id} className="py-3">
                          <CommentRow
                            comment={reply}
                            postId={post.id}
                            currentUserId={user?.id}
                            liked={commentLikes[reply.id] ?? reply.liked}
                            onLike={() => void toggleCommentLike(reply)}
                            onReply={() => setReplyTo(reply)}
                            confirming={confirmCommentId === reply.id}
                            onDelete={() => void deleteComment(reply)}
                          />
                          {replyTo?.id === reply.id ? (
                            <CommentComposer
                              postId={post.id}
                              parentCommentId={reply.id}
                              parentNickname={reply.nickname}
                              className="mt-3"
                              onDone={() => setReplyTo(null)}
                            />
                          ) : null}
                        </div>
                      ))}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}

function PostBody({ post }: { post: PostDetail }) {
  const parts = post.content.split(/(\[\[ref:\d+\]\])/g);
  return (
    <div className="mt-6 whitespace-pre-wrap text-[14px] leading-[2.05] text-ink-soft">
      {parts.map((part, index) => {
        const match = /^\[\[ref:(\d+)\]\]$/.exec(part);
        if (match) {
          const ref = post.refs[Number(match[1])];
          return ref ? <RefCard key={index} item={ref} /> : <span key={index}>{part}</span>;
        }
        return <span key={index}>{part}</span>;
      })}
    </div>
  );
}

function RefCard({ item }: { item: PostRef }) {
  return (
    <Link
      to={`/novel/${item.novelId}`}
      className="my-4 flex max-w-[440px] gap-3 rounded-[8px] border border-line bg-surface/60 p-3 transition-colors hover:border-line-strong"
    >
      <NovelCover
        novel={{
          title: item.title,
          authorName: item.authorName,
          coverKey: item.coverKey,
          categoryId: 0,
        }}
        className="w-12 shrink-0"
      />
      <div className="min-w-0 py-0.5">
        <p className="truncate font-serif text-[14px] text-ink">{item.title}</p>
        <p className="mt-0.5 truncate text-[11px] text-muted">{item.authorName}</p>
        <p className="mt-1 text-[10.5px] text-faint">{formatRating(item.avgRating)} / 5</p>
      </div>
    </Link>
  );
}

function CommentRow({
  comment,
  postId,
  currentUserId,
  liked,
  onLike,
  onReply,
  confirming,
  onDelete,
}: {
  comment: PostComment;
  postId: number;
  currentUserId?: number;
  liked: boolean;
  onLike: () => void;
  onReply: () => void;
  confirming: boolean;
  onDelete: () => void;
}) {
  return (
    <div>
      <div className="flex items-center gap-2.5">
        <Link to={`/users/${comment.userId}`}>
          <Avatar name={comment.nickname} avatarKey={comment.avatarKey} size={26} />
        </Link>
        <Link
          to={`/users/${comment.userId}`}
          className="text-[12px] text-ink-soft transition-colors hover:text-accent"
        >
          {comment.nickname}
        </Link>
        {comment.parentNickname ? (
          <span className="text-[10.5px] text-faint">
            回复 @{comment.parentDeleted ? '已删除的评论' : comment.parentNickname}
          </span>
        ) : null}
        <span className="ml-auto text-[10px] text-faint">
          {formatRelativeTime(comment.createdAt)}
        </span>
      </div>
      <p
        className={cn(
          'mt-2 text-[13px] leading-[1.9] text-ink-soft',
          comment.parentDeleted && 'text-faint',
        )}
      >
        {comment.content}
      </p>
      <div className="mt-2 flex items-center gap-4 text-[11px] text-muted">
        <button
          type="button"
          onClick={onLike}
          className={cn(
            'inline-flex items-center gap-1 transition-colors hover:text-accent',
            liked && 'text-accent',
          )}
          aria-pressed={liked}
        >
          <Heart size={11} fill={liked ? 'currentColor' : 'none'} />
          {comment.likeCount + (liked === comment.liked ? 0 : liked ? 1 : -1)}
        </button>
        <button
          type="button"
          onClick={onReply}
          className="inline-flex items-center gap-1 transition-colors hover:text-accent"
        >
          回复
        </button>
        {currentUserId === comment.userId ? (
          <button
            type="button"
            onClick={onDelete}
            className={cn(
              'inline-flex items-center gap-1 transition-colors hover:text-danger',
              confirming && 'text-danger',
            )}
          >
            <Trash2 size={11} />
            {confirming ? '确认删除' : '删除'}
          </button>
        ) : null}
        <span className="sr-only">帖子 {postId}</span>
      </div>
    </div>
  );
}

function CommentComposer({
  postId,
  parentCommentId,
  parentNickname,
  className,
  onDone,
}: {
  postId: number;
  parentCommentId?: number;
  parentNickname?: string;
  className?: string;
  onDone: () => void;
}) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [value, setValue] = useState('');
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const content = value.trim();
    if (!content) return;
    if (content.length > 1000) {
      toast('评论不能超过 1000 字', 'error');
      return;
    }
    setSaving(true);
    try {
      await api.createPostComment(postId, content, parentCommentId);
      setValue('');
      toast(parentCommentId ? '回复已发送' : '评论已发送');
      await queryClient.invalidateQueries({ queryKey: ['post-comments', postId] });
      onDone();
    } catch (error) {
      toast(errorMessage(error), 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className={cn('rounded-[8px] border border-line bg-surface/50 p-3', className)}>
      {parentNickname ? (
        <p className="mb-2 text-[10.5px] text-faint">回复 @{parentNickname}</p>
      ) : null}
      <textarea
        ref={inputRef}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        rows={3}
        maxLength={1000}
        placeholder={parentNickname ? `回复 @${parentNickname}…` : '写下你的想法…'}
        className="w-full resize-none bg-transparent text-[13px] leading-[1.85] text-ink placeholder:text-faint focus:outline-none"
      />
      <div className="mt-2 flex items-center justify-between">
        <span className="text-[10.5px] text-faint">{value.length} / 1000</span>
        <div className="flex gap-2">
          {parentNickname ? (
            <Button type="button" variant="ghost" size="sm" onClick={onDone}>
              取消
            </Button>
          ) : null}
          <Button type="submit" size="sm" disabled={saving || !value.trim()}>
            {saving ? '发送中…' : parentNickname ? '回复' : '发表评论'}
          </Button>
        </div>
      </div>
    </form>
  );
}
