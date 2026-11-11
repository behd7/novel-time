import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Heart, MessageCircle, PenLine, Trash2, X } from 'lucide-react';
import type { Review, ReviewReply } from '@/api/types';
import { api } from '@/api/endpoints';
import { useAuth } from '@/features/auth/AuthProvider';
import { useReviewReplies, useReviews } from '@/api/queries';
import { useToast } from '@/components/ui/Toast';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError, errorMessage } from '@/components/ui/LoadError';
import { Segmented } from '@/components/ui/Segmented';
import { Skeleton } from '@/components/ui/Skeleton';
import { Stars, StarPicker } from '@/components/ui/Stars';
import { formatRelativeTime } from '@/lib/format';
import { cn } from '@/lib/cn';

type ReviewSort = 'hot' | 'latest';

const SORTS: { value: ReviewSort; label: string }[] = [
  { value: 'hot', label: '最热' },
  { value: 'latest', label: '最新' },
];

export function ReviewSection({ novelId }: { novelId: number }) {
  const { user } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  const [sort, setSort] = useState<ReviewSort>('hot');
  const [composerOpen, setComposerOpen] = useState(false);
  const [score, setScore] = useState<number | null>(null);
  const [content, setContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [likes, setLikes] = useState<Record<number, boolean>>({});
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const [openReplies, setOpenReplies] = useState<Record<number, boolean>>({});

  const { data, isLoading, isError, error, refetch } = useReviews(novelId, {
    sort,
    page: 1,
    size: 20,
  });
  const reviews = data?.list ?? [];

  const requireLogin = () => {
    toast('登录后才能操作', 'error');
    navigate('/login', { state: { from: location.pathname } });
  };

  const submitReview = async (event: FormEvent) => {
    event.preventDefault();
    if (!user) return requireLogin();
    const next = content.trim();
    if (!next) {
      setFormError('书评内容不能为空');
      return;
    }
    if (next.length > 2000) {
      setFormError('书评不能超过 2000 字');
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await api.createReview(novelId, next, score ?? undefined);
      setContent('');
      setScore(null);
      setComposerOpen(false);
      toast('书评已发布');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['reviews', novelId] }),
        queryClient.invalidateQueries({ queryKey: ['rating', novelId] }),
      ]);
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  };

  const toggleLike = async (review: Review) => {
    if (!user) return requireLogin();
    const current = likes[review.id] ?? review.liked;
    setLikes((all) => ({ ...all, [review.id]: !current }));
    try {
      if (current) await api.unlikeReview(review.id);
      else await api.likeReview(review.id);
    } catch (caught) {
      setLikes((all) => ({ ...all, [review.id]: current }));
      toast(errorMessage(caught), 'error');
    }
  };

  const deleteReview = async (review: Review) => {
    if (confirmId !== review.id) {
      setConfirmId(review.id);
      return;
    }
    try {
      await api.deleteReview(review.id);
      setConfirmId(null);
      toast('书评已删除');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['reviews', novelId] }),
        queryClient.invalidateQueries({ queryKey: ['rating', novelId] }),
      ]);
    } catch (caught) {
      toast(errorMessage(caught), 'error');
    }
  };

  return (
    <div className="py-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <Segmented
          options={SORTS}
          value={sort}
          onChange={setSort}
          size="sm"
        />
        <Button
          variant={composerOpen ? 'secondary' : 'primary'}
          size="sm"
          onClick={() => (user ? setComposerOpen((open) => !open) : requireLogin())}
        >
          {composerOpen ? <X size={13} /> : <PenLine size={13} />}
          {composerOpen ? '收起' : '写书评'}
        </Button>
      </div>

      {composerOpen ? (
        <form
          onSubmit={submitReview}
          className="mt-4 rounded-[8px] border border-line bg-surface/60 p-4"
        >
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-[11.5px] text-muted">评分（可选）</span>
            <StarPicker value={score} onChange={setScore} size={18} />
            {score != null ? (
              <button
                type="button"
                onClick={() => setScore(null)}
                className="text-[11px] text-faint transition-colors hover:text-accent"
              >
                清除评分
              </button>
            ) : null}
          </div>
          <textarea
            value={content}
            onChange={(event) => setContent(event.target.value)}
            rows={4}
            maxLength={2000}
            placeholder="说说这本书哪里好，或者哪里没读下去…"
            className="mt-3 w-full resize-none bg-transparent text-[13.5px] leading-[1.9] text-ink placeholder:text-faint focus:outline-none"
          />
          {formError ? <p className="mt-2 text-[11.5px] text-danger">{formError}</p> : null}
          <div className="mt-3 flex items-center justify-between">
            <span className="text-[10.5px] text-faint">{content.length} / 2000</span>
            <Button type="submit" size="sm" disabled={saving || !content.trim()}>
              {saving ? '发布中…' : '发布书评'}
            </Button>
          </div>
        </form>
      ) : null}

      {isError ? (
        <LoadError className="mt-6" error={error} onRetry={() => void refetch()} />
      ) : isLoading ? (
        <div className="mt-6 space-y-5">
          {Array.from({ length: 3 }, (_, index) => (
            <div key={index} className="space-y-3 border-b border-line/60 py-5">
              <Skeleton className="h-8 w-40" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3" />
            </div>
          ))}
        </div>
      ) : reviews.length === 0 ? (
        <EmptyState
          className="mt-6"
          icon={<MessageCircle size={24} strokeWidth={1.3} />}
          title="还没有人写书评"
          description="第一条书评往往决定了后来的人要不要翻开这本书。"
        />
      ) : (
        <div className="mt-2">
          {reviews.map((review) => {
            const liked = likes[review.id] ?? review.liked;
            const likeDelta = liked === review.liked ? 0 : liked ? 1 : -1;
            const isMine = user?.id === review.userId;
            return (
              <article key={review.id} className="border-b border-line/60 py-5 first:pt-3">
                <div className="flex items-center gap-3">
                  <Link to={`/users/${review.userId}`}>
                    <Avatar name={review.nickname} avatarKey={review.avatarKey} size={32} />
                  </Link>
                  <div>
                    <Link
                      to={`/users/${review.userId}`}
                      className="text-[13px] text-ink transition-colors hover:text-accent"
                    >
                      {review.nickname}
                    </Link>
                    <div className="mt-0.5 flex items-center gap-2 text-[11px] text-faint">
                      {review.score ? (
                        <>
                          <Stars value={review.score} size={10} />
                          <span>{review.score} 星</span>
                        </>
                      ) : null}
                      <span>{formatRelativeTime(review.createdAt)}</span>
                    </div>
                  </div>
                  {isMine ? (
                    <button
                      type="button"
                      onClick={() => void deleteReview(review)}
                      className={cn(
                        'ml-auto inline-flex items-center gap-1 text-[11px] text-faint transition-colors hover:text-danger',
                        confirmId === review.id && 'text-danger',
                      )}
                    >
                      <Trash2 size={11} />
                      {confirmId === review.id ? '确认删除' : '删除'}
                    </button>
                  ) : null}
                </div>

                <p className="mt-3 text-[13.5px] leading-[1.95] text-ink-soft">{review.content}</p>

                <div className="mt-3 flex items-center gap-5 text-[11.5px] text-muted">
                  <button
                    type="button"
                    onClick={() => void toggleLike(review)}
                    className={cn(
                      'inline-flex items-center gap-1.5 transition-colors hover:text-accent',
                      liked && 'text-accent',
                    )}
                    aria-pressed={liked}
                  >
                    <Heart size={12} fill={liked ? 'currentColor' : 'none'} />
                    {review.likeCount + likeDelta}
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      setOpenReplies((all) => ({ ...all, [review.id]: !all[review.id] }))
                    }
                    className="inline-flex items-center gap-1.5 transition-colors hover:text-accent"
                  >
                    <MessageCircle size={12} />
                    {review.replyCount} 条回复
                  </button>
                </div>

                {openReplies[review.id] ? (
                  <ReviewReplies reviewId={review.id} novelId={novelId} />
                ) : null}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}

function ReviewReplies({ reviewId, novelId }: { reviewId: number; novelId: number }) {
  const { user } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { data, isLoading, isError, error, refetch } = useReviewReplies(reviewId, true);

  const [value, setValue] = useState('');
  const [saving, setSaving] = useState(false);
  const [parentReply, setParentReply] = useState<ReviewReply | null>(null);
  const [likes, setLikes] = useState<Record<number, boolean>>({});
  const [confirmId, setConfirmId] = useState<number | null>(null);

  const replies = data?.list ?? [];

  const requireLogin = () => {
    toast('登录后才能操作', 'error');
    navigate('/login', { state: { from: location.pathname } });
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!user) return requireLogin();
    const content = value.trim();
    if (!content) return;
    if (content.length > 1000) {
      toast('回复不能超过 1000 字', 'error');
      return;
    }
    setSaving(true);
    try {
      await api.createReply(reviewId, content, parentReply?.id);
      setValue('');
      setParentReply(null);
      toast('回复已发送');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['review-replies', reviewId] }),
        queryClient.invalidateQueries({ queryKey: ['reviews', novelId] }),
      ]);
    } catch (caught) {
      toast(errorMessage(caught), 'error');
    } finally {
      setSaving(false);
    }
  };

  const toggleLike = async (reply: ReviewReply) => {
    if (!user) return requireLogin();
    const current = likes[reply.id] ?? reply.liked;
    setLikes((all) => ({ ...all, [reply.id]: !current }));
    try {
      if (current) await api.unlikeReply(reply.id);
      else await api.likeReply(reply.id);
    } catch (caught) {
      setLikes((all) => ({ ...all, [reply.id]: current }));
      toast(errorMessage(caught), 'error');
    }
  };

  const deleteReply = async (reply: ReviewReply) => {
    if (confirmId !== reply.id) {
      setConfirmId(reply.id);
      return;
    }
    try {
      await api.deleteReply(reply.id);
      setConfirmId(null);
      toast('回复已删除');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['review-replies', reviewId] }),
        queryClient.invalidateQueries({ queryKey: ['reviews', novelId] }),
      ]);
    } catch (caught) {
      toast(errorMessage(caught), 'error');
    }
  };

  return (
    <div className="mt-4 rounded-[8px] border border-line bg-paper-deep/40 p-4">
      {isError ? (
        <LoadError error={error} onRetry={() => void refetch()} />
      ) : isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      ) : replies.length === 0 ? (
        <p className="text-[12px] text-faint">还没有回复，来当第一个。</p>
      ) : (
        <div className="space-y-3">
          {replies.map((reply) => {
            const liked = likes[reply.id] ?? reply.liked;
            const isMine = user?.id === reply.userId;
            return (
              <div key={reply.id} className="border-b border-line/50 pb-3 last:border-b-0 last:pb-0">
                <div className="flex items-center gap-2">
                  <Link to={`/users/${reply.userId}`}>
                    <Avatar name={reply.nickname} avatarKey={reply.avatarKey} size={24} />
                  </Link>
                  <Link
                    to={`/users/${reply.userId}`}
                    className="text-[11.5px] text-ink-soft transition-colors hover:text-accent"
                  >
                    {reply.nickname}
                  </Link>
                  {reply.parentNickname ? (
                    <span className="text-[10.5px] text-faint">
                      回复 @{reply.parentNickname}
                    </span>
                  ) : null}
                  <span className="ml-auto text-[10px] text-faint">
                    {formatRelativeTime(reply.createdAt)}
                  </span>
                </div>
                <p className="mt-1.5 text-[12.5px] leading-[1.85] text-ink-soft">{reply.content}</p>
                <div className="mt-1.5 flex items-center gap-4 text-[10.5px] text-muted">
                  <button
                    type="button"
                    onClick={() => void toggleLike(reply)}
                    className={cn(
                      'inline-flex items-center gap-1 transition-colors hover:text-accent',
                      liked && 'text-accent',
                    )}
                  >
                    <Heart size={10} fill={liked ? 'currentColor' : 'none'} />
                    {reply.likeCount + (liked === reply.liked ? 0 : liked ? 1 : -1)}
                  </button>
                  <button
                    type="button"
                    onClick={() => setParentReply(reply)}
                    className="transition-colors hover:text-accent"
                  >
                    回复
                  </button>
                  {isMine ? (
                    <button
                      type="button"
                      onClick={() => void deleteReply(reply)}
                      className={cn(
                        'transition-colors hover:text-danger',
                        confirmId === reply.id && 'text-danger',
                      )}
                    >
                      {confirmId === reply.id ? '确认删除' : '删除'}
                    </button>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {user ? (
        <form onSubmit={submit} className="mt-4 border-t border-line/60 pt-3">
          {parentReply ? (
            <div className="mb-2 flex items-center gap-2 text-[10.5px] text-faint">
              回复 @{parentReply.nickname}
              <button
                type="button"
                onClick={() => setParentReply(null)}
                className="text-accent hover:text-accent-deep"
              >
                取消
              </button>
            </div>
          ) : null}
          <div className="flex items-end gap-3">
            <textarea
              value={value}
              onChange={(event) => setValue(event.target.value)}
              rows={2}
              maxLength={1000}
              placeholder={parentReply ? `回复 @${parentReply.nickname}…` : '写一条回复…'}
              className="min-h-[56px] flex-1 resize-none rounded-[5px] border border-line bg-surface px-3 py-2 text-[12.5px] leading-[1.8] text-ink placeholder:text-faint focus:border-line-strong focus:outline-none"
            />
            <Button type="submit" size="sm" disabled={saving || !value.trim()}>
              {saving ? '发送中…' : '回复'}
            </Button>
          </div>
        </form>
      ) : (
        <button
          type="button"
          onClick={requireLogin}
          className="mt-3 text-[11.5px] text-accent transition-colors hover:text-accent-deep"
        >
          登录后回复
        </button>
      )}
    </div>
  );
}
