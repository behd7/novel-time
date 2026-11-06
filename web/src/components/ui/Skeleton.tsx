import { cn } from '@/lib/cn';

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-[4px] bg-line/70', className)} />;
}

export function NovelCardSkeleton() {
  return (
    <div>
      <Skeleton className="aspect-[3/4] w-full" />
      <Skeleton className="mt-3 h-4 w-3/4" />
      <Skeleton className="mt-2 h-3 w-1/2" />
    </div>
  );
}

export function NovelRowSkeleton() {
  return (
    <div className="flex gap-4 py-4">
      <Skeleton className="h-[88px] w-[66px] shrink-0" />
      <div className="flex-1 space-y-2 py-1">
        <Skeleton className="h-4 w-1/3" />
        <Skeleton className="h-3 w-1/4" />
        <Skeleton className="h-3 w-2/3" />
      </div>
    </div>
  );
}
