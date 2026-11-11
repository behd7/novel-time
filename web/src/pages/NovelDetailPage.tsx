import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  Check,
  ChevronDown,
  MessageCircle,
  Plus,
  Star,
  ThumbsUp,
} from 'lucide-react';
import { formatCount, formatRating, formatRelativeTime, formatWordCount } from '@/lib/format';
import { designCover } from '@/lib/cover';
import { cn } from '@/lib/cn';
import { loadProgress } from '@/lib/progress';
import { useAuth } from '@/features/auth/AuthProvider';
import { useCategoriesContext } from '@/features/categories/CategoryProvider';
import { api } from '@/api/endpoints';
import { ApiError } from '@/api/client';
import {
  qk,
  useBookshelf,
  useChapters,
  useNovel,
  useNovels,
  useRating,
  useReviews,
} from '@/api/queries';
import { useToast } from '@/components/ui/Toast';
import { NovelCover } from '@/components/book/NovelCover';
import { NovelCard } from '@/components/book/NovelCard';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Stars, StarPicker } from '@/components/ui/Stars';
import { Tag, StatusDot } from '@/components/ui/Tag';
import { UnderlineTabs } from '@/components/ui/Segmented';
import { Avatar } from '@/components/ui/Avatar';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError, errorMessage } from '@/components/ui/LoadError';
import { Skeleton } from '@/components/ui/Skeleton';
import { NotFoundPage } from './NotFoundPage';

type Tab = 'catalog' | 'reviews';

export function NovelDetailPage() {
  const { id } = useParams();
  const novelId = Number(id);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { nameOf, topOf } = useCategoriesContext();
  const { toast } = useToast();

  const novelQuery = useNovel(novelId);
  const chaptersQuery = useChapters(novelId, { page: 1, size: 12 });
  const ratingQuery = useRating(novelId);
  const shelfQuery = useBookshelf();
  const [tab, setTab] = useState<Tab>('catalog');
  const [expanded, setExpanded] = useState(false);
  const [ratingOpen, setRatingOpen] = useState(false);
  const [showAllReviews, setShowAllReviews] = useState(false);
  const reviewsQuery = useReviews(novelId, {
    sort: 'hot',
    page: 1,
    size: showAllReviews ? 20 : 3,
  });

  const novel = novelQuery.data;
  const top = novel ? topOf(novel.categoryId) : undefined;
  const similarQuery = useNovels(
    { categoryId: top?.id, sort: 'hot', size: 5 },
    Boolean(top),
  );

  const [myRatingOverride, setMyRatingOverride] = useState<number | null | undefined>(undefined);
  const [shelfOverride, setShelfOverride] = useState<boolean | undefined>(undefined);
  const [liked, setLiked] = useState<Record<number, boolean>>({});
  const [ratingSaving, setRatingSaving] = useState(false);
  const [shelfSaving, setShelfSaving] = useState(false);

  if (!Number.isFinite(novelId) || novelId <= 0) {
    return <NotFoundPage />;
  }

  if (novelQuery.isLoading) {
    return (
      <div className="mx-auto max-w-[1240px] px-6 py-10 lg:px-10">
        <Skeleton className="h-4 w-44" />
        <div className="mt-8 grid gap-10 sm:grid-cols-[220px_1fr] lg:gap-14">
          <Skeleton className="aspect-[3/4] w-full max-w-[220px]" />
          <div className="space-y-4">
            <Skeleton className="h-6 w-32" />
            <Skeleton className="h-10 w-3/5" />
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-20 w-full" />
            <div className="flex gap-3 pt-3">
              <Skeleton className="h-12 w-40" />
              <Skeleton className="h-12 w-32" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (novelQuery.isError) {
    const code = novelQuery.error instanceof ApiError ? novelQuery.error.code : 0;
    if (code === 10201 || code === 10202) return <NotFoundPage />;
    return (
      <div className="mx-auto max-w-[1240px] px-6 py-16 lg:px-10">
        <LoadError error={novelQuery.error} onRetry={() => void novelQuery.refetch()} />
      </div>
    );
  }

  if (!novel) return <NotFoundPage />;

  const design = designCover(novel.title, novel.authorName);
  const shelfItem = shelfQuery.data?.list.find((item) => item.novelId === novelId);
  const inShelf = shelfOverride ?? Boolean(shelfItem);
  /** 本地进度优先：匿名阅读也算数，登录后书架里的进度只是兜底。 */
  const lastReadSeq = loadProgress(novelId) ?? shelfItem?.lastChapterSeq ?? null;
  const startSeq = lastReadSeq ?? 1;

  const rating = ratingQuery.data;
  const avgRating = rating?.avgRating ?? novel.avgRating;
  const ratingCount = rating?.ratingCount ?? novel.ratingCount;
  const myRating =
    myRatingOverride !== undefined ? myRatingOverride : (rating?.myScore ?? null);

  const chapters = chaptersQuery.data?.list ?? [];
  const reviews = reviewsQuery.data?.list ?? [];
  const reviewTotal = reviewsQuery.data?.total ?? 0;
  const similar = (similarQuery.data?.list ?? []).filter((item) => item.id !== novelId).slice(0, 4);

  const requireLogin = () => {
    toast('登录后才能操作', 'error');
    navigate('/login', { state: { from: location.pathname } });
  };

  const submitRating = async (score: number) => {
    if (!user) {
      requireLogin();
      return;
    }
    if (ratingSaving) return;
    setRatingSaving(true);
    try {
      await api.rate(novelId, score);
      setMyRatingOverride(score);
      setRatingOpen(false);
      toast(`已评 ${score} 星`);
      // 只刷评分摘要；不要再刷详情，详情接口每次调用都会让阅读数 +1
      await queryClient.invalidateQueries({ queryKey: qk.rating(novelId) });
    } catch (error) {
      toast(errorMessage(error), 'error');
    } finally {
      setRatingSaving(false);
    }
  };

  const toggleShelf = async () => {
    if (!user) {
      requireLogin();
      return;
    }
    if (shelfSaving) return;
    setShelfSaving(true);
    const next = !inShelf;
    try {
      if (next) {
        await api.addToShelf(novelId);
      } else {
        await api.removeFromShelf(novelId);
      }
      setShelfOverride(next);
      toast(next ? '已加入书架' : '已从书架移除');
      await queryClient.invalidateQueries({ queryKey: qk.bookshelf });
    } catch (error) {
      // 并发下"已在书架"是正常结果，不当成失败弹红
      if (next && error instanceof ApiError && /已在书架/.test(error.message)) {
        setShelfOverride(true);
        await queryClient.invalidateQueries({ queryKey: qk.bookshelf });
      } else {
        toast(errorMessage(error), 'error');
      }
    } finally {
      setShelfSaving(false);
    }
  };

  const toggleLike = async (reviewId: number, currentlyLiked: boolean) => {
    if (!user) {
      requireLogin();
      return;
    }
    setLiked((current) => ({ ...current, [reviewId]: !currentlyLiked }));
    try {
      if (currentlyLiked) {
        await api.unlikeReview(reviewId);
      } else {
        await api.likeReview(reviewId);
      }
      await queryClient.invalidateQueries({ queryKey: ['reviews', novelId] });
    } catch (error) {
      setLiked((current) => ({ ...current, [reviewId]: currentlyLiked }));
      toast(errorMessage(error), 'error');
    }
  };

  return (
    <div>
      {/* 顶部染色：跟着封面走，让详情页和书本身产生联系 */}
      <div
        className="border-b border-line/60"
        style={{
          background: `linear-gradient(180deg, ${design.wash} 0%, transparent 100%)`,
        }}
      >
        <div className="mx-auto max-w-[1240px] px-6 pb-12 pt-8 lg:px-10 lg:pb-16 lg:pt-10">
          <nav className="flex items-center gap-2 text-[11.5px] text-muted" aria-label="面包屑">
            <Link to="/browse" className="transition-colors hover:text-ink">
              书库
            </Link>
            <span className="text-faint">/</span>
            <Link
              to={`/browse?category=${top?.id ?? novel.categoryId}`}
              className="transition-colors hover:text-ink"
            >
              {top?.name ?? nameOf(novel.categoryId)}
            </Link>
            <span className="text-faint">/</span>
            <span className="truncate text-ink-soft">{novel.title}</span>
          </nav>

          <div className="mt-8 grid gap-10 sm:grid-cols-[220px_1fr] lg:gap-14">
            <div className="mx-auto w-[190px] sm:mx-0 sm:w-full">
              <NovelCover novel={novel} />
              <div className="mt-4 flex items-center justify-between text-[11px] text-faint">
                <span>{novel.chapterCount} 章</span>
                <span>{formatWordCount(novel.wordCount)}</span>
              </div>
            </div>

            <div>
              <div className="flex flex-wrap items-center gap-2">
                <Tag tone="accent">{nameOf(novel.categoryId)}</Tag>
                <StatusDot status={novel.status} />
              </div>

              <h1 className="mt-4 font-serif text-[34px] font-semibold leading-[1.18] tracking-[-0.01em] text-ink sm:text-[42px]">
                {novel.title}
              </h1>
              <p className="mt-3 text-[13px] text-muted">
                {novel.authorName} 著
                <span className="mx-2 text-line-strong">·</span>
                更新于 {formatRelativeTime(novel.latestUpdateAt)}
              </p>

              <div className="mt-5 flex flex-wrap items-end gap-x-8 gap-y-4">
                <div>
                  <div className="flex items-baseline gap-2">
                    <span className="font-serif text-[40px] leading-none text-ink">
                      {formatRating(avgRating)}
                    </span>
                    {avgRating > 0 ? <span className="text-[12px] text-faint">/ 5</span> : null}
                  </div>
                  <div className="mt-2 flex items-center gap-2">
                    <Stars value={avgRating} size={14} />
                    <span className="text-[11.5px] text-muted">
                      {formatCount(ratingCount)} 人评分
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => (user ? setRatingOpen((open) => !open) : requireLogin())}
                  disabled={ratingSaving}
                  className="group inline-flex items-center gap-2 text-[12.5px] text-muted transition-colors hover:text-accent disabled:opacity-50"
                >
                  <span className="flex h-9 w-9 items-center justify-center rounded-full border border-line-strong transition-colors group-hover:border-accent/50 group-hover:bg-accent-soft">
                    <Star size={14} />
                  </span>
                  {myRating ? `你打了 ${myRating} 星` : '给它打分'}
                </button>
              </div>

              {ratingOpen ? (
                <div className="mt-3 inline-flex items-center gap-3 rounded-[8px] border border-line bg-surface px-4 py-2.5">
                  <StarPicker value={myRating} onChange={submitRating} size={20} />
                  <span className="text-[11.5px] text-muted">点击星星评分</span>
                </div>
              ) : null}

              <div className="mt-7 flex flex-wrap items-center gap-3">
                <ButtonLink
                  to={`/novel/${novel.id}/chapters/${startSeq}`}
                  size="lg"
                  className="w-full sm:w-auto"
                >
                  {lastReadSeq ? `继续阅读 · 第 ${startSeq} 章` : '开始阅读'}
                  <ArrowRight size={15} />
                </ButtonLink>
                <Button
                  variant="secondary"
                  size="lg"
                  onClick={() => void toggleShelf()}
                  disabled={shelfSaving}
                  className="w-full sm:w-auto"
                >
                  {inShelf ? <Check size={15} /> : <Plus size={15} />}
                  {inShelf ? '已在书架' : '加入书架'}
                </Button>
              </div>

              <div className="mt-8 grid grid-cols-2 gap-y-4 border-t border-line/80 pt-5 text-[12px] sm:grid-cols-4">
                <Stat label="阅读" value={formatCount(novel.viewCount)} />
                <Stat label="收藏" value={formatCount(novel.favoriteCount)} />
                <Stat label="章节" value={`${novel.chapterCount} 章`} />
                <Stat label="字数" value={formatWordCount(novel.wordCount)} />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-[1240px] px-6 lg:px-10">
        {/* 简介 */}
        <section className="border-b border-line/70 py-8">
          <div className="grid gap-6 sm:grid-cols-[120px_1fr]">
            <p className="pt-0.5 text-[10.5px] tracking-[0.24em] text-faint">内容简介</p>
            <div>
              <p
                className={cn(
                  'max-w-[46rem] text-pretty text-[13.5px] leading-[2] text-ink-soft',
                  !expanded && 'line-clamp-3',
                )}
              >
                {novel.intro}
              </p>
              <div className="mt-3 flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  onClick={() => setExpanded((open) => !open)}
                  className="inline-flex items-center gap-1 text-[11.5px] text-muted transition-colors hover:text-accent"
                >
                  {expanded ? '收起' : '展开'}
                  <ChevronDown
                    size={13}
                    className={cn('transition-transform', expanded && 'rotate-180')}
                  />
                </button>
                <span className="text-line-strong">·</span>
                <div className="flex flex-wrap gap-1.5">
                  <Tag>{nameOf(novel.categoryId)}</Tag>
                  <Tag>{novel.status === 1 ? '已完结' : '连载中'}</Tag>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* 目录 / 书评 */}
        <section className="pt-8">
          <UnderlineTabs
            options={[
              { value: 'catalog', label: '目录', hint: `${novel.chapterCount} 章` },
              { value: 'reviews', label: '书评', hint: `${formatCount(ratingCount)} 人评分` },
            ]}
            value={tab}
            onChange={setTab}
          />

          {tab === 'catalog' ? (
            <div className="py-6">
              <div className="flex items-center justify-between pb-2">
                <p className="text-[11.5px] text-muted">
                  {lastReadSeq ? `你上次读到第 ${lastReadSeq} 章` : '从第一章开始'}
                </p>
                <Link
                  to={`/novel/${novel.id}/chapters`}
                  className="inline-flex items-center gap-1 text-[12px] text-accent transition-colors hover:text-accent-deep"
                >
                  查看全部 {novel.chapterCount} 章
                  <ArrowRight size={13} />
                </Link>
              </div>

              {chaptersQuery.isError ? (
                <LoadError
                  className="mt-4"
                  error={chaptersQuery.error}
                  onRetry={() => void chaptersQuery.refetch()}
                />
              ) : chaptersQuery.isLoading ? (
                <div className="grid gap-x-10 sm:grid-cols-2">
                  {Array.from({ length: 8 }, (_, index) => (
                    <Skeleton key={index} className="my-3 h-6 w-full" />
                  ))}
                </div>
              ) : chapters.length === 0 ? (
                <EmptyState className="mt-4" title="目录还是空的" description="作者还没有发布章节。" />
              ) : (
                <div className="grid gap-x-10 sm:grid-cols-2">
                  {chapters.map((chapter) => {
                    const isLastRead = lastReadSeq === chapter.seq;
                    return (
                      <Link
                        key={chapter.seq}
                        to={`/novel/${novel.id}/chapters/${chapter.seq}`}
                        className="group flex items-center gap-3 border-b border-line/60 py-3"
                      >
                        <span
                          className={cn(
                            'w-8 shrink-0 font-serif text-[12.5px] tabular-nums',
                            isLastRead ? 'text-accent' : 'text-faint',
                          )}
                        >
                          {String(chapter.seq).padStart(2, '0')}
                        </span>
                        <span className="min-w-0 flex-1 truncate text-[13.5px] text-ink-soft transition-colors group-hover:text-accent">
                          {chapter.title}
                        </span>
                        {isLastRead ? <Tag tone="accent">上次读到</Tag> : null}
                        <span className="shrink-0 text-[11px] text-faint">
                          {chapter.wordCount} 字
                        </span>
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          ) : (
            <div className="py-6">
              {reviewsQuery.isError ? (
                <LoadError
                  error={reviewsQuery.error}
                  onRetry={() => void reviewsQuery.refetch()}
                />
              ) : reviewsQuery.isLoading ? (
                <div className="space-y-5">
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
                  icon={<MessageCircle size={24} strokeWidth={1.3} />}
                  title="还没有人写书评"
                  description="第一条书评往往决定了后来的人要不要翻开这本书。"
                />
              ) : (
                <div>
                  {reviews.map((review) => {
                    const isLiked = liked[review.id] ?? review.liked;
                    const likeDelta = isLiked === review.liked ? 0 : isLiked ? 1 : -1;
                    return (
                      <article key={review.id} className="border-b border-line/60 py-5 first:pt-1">
                        <div className="flex items-center gap-3">
                          <Avatar name={review.nickname} size={32} />
                          <div>
                            <p className="text-[13px] text-ink">{review.nickname}</p>
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
                        </div>
                        <p className="mt-3 text-[13.5px] leading-[1.95] text-ink-soft">
                          {review.content}
                        </p>
                        <div className="mt-3 flex items-center gap-5 text-[11.5px] text-muted">
                          <button
                            type="button"
                            onClick={() => void toggleLike(review.id, isLiked)}
                            className={cn(
                              'inline-flex items-center gap-1.5 transition-colors hover:text-accent',
                              isLiked && 'text-accent',
                            )}
                          >
                            <ThumbsUp size={12} />
                            {review.likeCount + likeDelta}
                          </button>
                          <span className="inline-flex items-center gap-1.5">
                            <MessageCircle size={12} />
                            {review.replyCount}
                          </span>
                        </div>
                      </article>
                    );
                  })}
                  {reviewTotal > 3 && !showAllReviews ? (
                    <button
                      type="button"
                      onClick={() => setShowAllReviews(true)}
                      className="mt-5 text-[12.5px] text-accent transition-colors hover:text-accent-deep"
                    >
                      查看全部 {reviewTotal} 条书评
                    </button>
                  ) : null}
                </div>
              )}
            </div>
          )}
        </section>

        {/* 相似推荐 */}
        <section className="border-t border-line/70 py-12">
          <div className="flex items-end justify-between">
            <h2 className="font-serif text-[22px] text-ink">你可能也会喜欢</h2>
            <Link
              to="/browse"
              className="text-[11.5px] text-muted transition-colors hover:text-accent"
            >
              去书库逛逛
            </Link>
          </div>
          {similarQuery.isLoading ? (
            <div className="mt-7 grid grid-cols-2 gap-x-6 gap-y-9 sm:grid-cols-4">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="aspect-[3/4] w-full" />
              ))}
            </div>
          ) : similar.length === 0 ? (
            <p className="mt-6 text-[12.5px] text-muted">同类作品还不多，去书库看看别的题材。</p>
          ) : (
            <div className="mt-7 grid grid-cols-2 gap-x-6 gap-y-9 sm:grid-cols-4">
              {similar.map((item) => (
                <NovelCard key={item.id} novel={item} showRating={false} />
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[10.5px] tracking-[0.18em] text-faint">{label}</p>
      <p className="mt-1 text-[13px] text-ink-soft">{value}</p>
    </div>
  );
}
