import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/cn';

interface PaginationProps {
  page: number;
  total: number;
  size: number;
  onChange: (page: number) => void;
  className?: string;
}

/** 省略号只省略中间段：首尾始终可见，用户不会"翻着翻着不知道自己在第几页"。 */
function pageWindow(page: number, totalPages: number): (number | 'gap')[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }
  const pages = new Set<number>([1, totalPages, page - 1, page, page + 1]);
  const sorted = [...pages].filter((value) => value >= 1 && value <= totalPages).sort((a, b) => a - b);
  const result: (number | 'gap')[] = [];
  sorted.forEach((value, index) => {
    if (index > 0 && value - sorted[index - 1] > 1) result.push('gap');
    result.push(value);
  });
  return result;
}

export function Pagination({ page, total, size, onChange, className }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / Math.max(1, size)));
  if (totalPages <= 1) return null;

  return (
    <nav className={cn('flex items-center justify-center gap-1', className)} aria-label="分页">
      <button
        type="button"
        onClick={() => onChange(page - 1)}
        disabled={page <= 1}
        className="inline-flex h-9 w-9 items-center justify-center rounded-[6px] text-muted transition-colors hover:bg-paper-deep hover:text-ink disabled:pointer-events-none disabled:opacity-30"
        aria-label="上一页"
      >
        <ChevronLeft size={16} />
      </button>

      {pageWindow(page, totalPages).map((item, index) =>
        item === 'gap' ? (
          <span key={`gap-${index}`} className="px-1 text-xs text-faint">
            …
          </span>
        ) : (
          <button
            key={item}
            type="button"
            onClick={() => onChange(item)}
            aria-current={item === page ? 'page' : undefined}
            className={cn(
              'inline-flex h-9 min-w-9 items-center justify-center rounded-[6px] px-2 text-[13px] transition-colors',
              item === page
                ? 'bg-ink text-paper'
                : 'text-muted hover:bg-paper-deep hover:text-ink',
            )}
          >
            {item}
          </button>
        ),
      )}

      <button
        type="button"
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages}
        className="inline-flex h-9 w-9 items-center justify-center rounded-[6px] text-muted transition-colors hover:bg-paper-deep hover:text-ink disabled:pointer-events-none disabled:opacity-30"
        aria-label="下一页"
      >
        <ChevronRight size={16} />
      </button>
    </nav>
  );
}
