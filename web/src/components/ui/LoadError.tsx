import { AlertCircle } from 'lucide-react';
import { ApiError } from '@/api/client';
import { Button } from './Button';
import { cn } from '@/lib/cn';

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message;
  }
  if (error instanceof Error) return error.message;
  return '未知错误，请稍后重试';
}

export function LoadError({
  error,
  onRetry,
  className,
}: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-[10px] border border-dashed border-line px-6 py-14 text-center',
        className,
      )}
    >
      <AlertCircle size={22} className="mb-3 text-faint" />
      <p className="font-serif text-lg text-ink-soft">加载失败</p>
      <p className="mt-2 max-w-md text-[13px] leading-relaxed text-muted">{errorMessage(error)}</p>
      {onRetry ? (
        <Button variant="secondary" size="sm" className="mt-5" onClick={onRetry}>
          重试
        </Button>
      ) : null}
    </div>
  );
}
