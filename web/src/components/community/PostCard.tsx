import { Link } from 'react-router-dom';
import { Eye, Heart, MessageCircle } from 'lucide-react';
import type { PostView } from '@/api/types';
import { formatCount, formatRelativeTime } from '@/lib/format';
import { cn } from '@/lib/cn';
import { Avatar } from '@/components/ui/Avatar';

export function PostCard({
  post,
  liked,
  likeCount,
  onLike,
  className,
}: {
  post: PostView;
  liked: boolean;
  likeCount: number;
  onLike: () => void;
  className?: string;
}) {
  return (
    <article
      className={cn(
        'group rounded-[10px] border border-line bg-surface/50 p-5 transition-colors hover:border-line-strong sm:p-6',
        className,
      )}
    >
      <div className="flex items-center gap-3">
        <Link to={`/users/${post.userId}`} className="shrink-0">
          <Avatar name={post.nickname} avatarKey={post.avatarKey} size={30} />
        </Link>
        <div className="min-w-0">
          <Link
            to={`/users/${post.userId}`}
            className="block truncate text-[12.5px] text-ink-soft transition-colors hover:text-accent"
          >
            {post.nickname}
          </Link>
          <p className="text-[10.5px] text-faint">{formatRelativeTime(post.createdAt)}</p>
        </div>
        <Link
          to={`/posts/${post.id}`}
          className="ml-auto text-[11px] text-faint transition-colors hover:text-accent"
        >
          查看讨论
        </Link>
      </div>

      <Link to={`/posts/${post.id}`} className="mt-4 block">
        <h2 className="font-serif text-[21px] font-medium leading-snug text-ink transition-colors group-hover:text-accent">
          {post.title}
        </h2>
        <p className="mt-2 line-clamp-2 text-[13px] leading-[1.9] text-muted">{post.summary}</p>
      </Link>

      <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-line/70 pt-3 text-[11.5px] text-muted">
        <button
          type="button"
          onClick={onLike}
          className={cn(
            'inline-flex h-7 items-center gap-1.5 rounded-full px-2 transition-colors hover:bg-paper-deep hover:text-accent',
            liked && 'text-accent',
          )}
          aria-pressed={liked}
          aria-label={liked ? '取消点赞' : '点赞'}
        >
          <Heart size={13} fill={liked ? 'currentColor' : 'none'} />
          {formatCount(likeCount)}
        </button>
        <Link
          to={`/posts/${post.id}`}
          className="inline-flex items-center gap-1.5 transition-colors hover:text-accent"
        >
          <MessageCircle size={13} />
          {formatCount(post.commentCount)}
        </Link>
        <span className="inline-flex items-center gap-1.5 text-faint">
          <Eye size={13} />
          {formatCount(post.viewCount)}
        </span>
      </div>
    </article>
  );
}
