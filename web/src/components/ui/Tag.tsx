import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

export function Tag({
  children,
  className,
  tone = 'default',
}: {
  children: ReactNode;
  className?: string;
  tone?: 'default' | 'accent' | 'gold';
}) {
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center rounded-[3px] border px-2 text-[11px] tracking-[0.08em]',
        tone === 'accent' && 'border-accent/25 bg-accent-soft text-accent',
        tone === 'gold' && 'border-gold/25 bg-gold-soft text-gold',
        tone === 'default' && 'border-line bg-transparent text-muted',
        className,
      )}
    >
      {children}
    </span>
  );
}

/** 状态点：连载中是一枚呼吸的绿点，已完结是灰点。 */
export function StatusDot({ status }: { status: number }) {
  const serializing = status === 0;
  return (
    <span className="inline-flex items-center gap-1.5 text-[11px] tracking-[0.08em] text-muted">
      <span
        className={cn(
          'inline-block h-1.5 w-1.5 rounded-full',
          serializing ? 'bg-accent' : 'bg-faint',
        )}
      />
      {serializing ? '连载中' : '已完结'}
    </span>
  );
}
