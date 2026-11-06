import { cn } from '@/lib/cn';

export interface SegmentedOption<T extends string> {
  value: T;
  label: string;
  hint?: string;
}

interface SegmentedProps<T extends string> {
  options: SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
  size?: 'sm' | 'md';
}

/** 胶囊分段器：榜单周期、搜索排序共用一套，避免出现两种"选中态"。 */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  className,
  size = 'md',
}: SegmentedProps<T>) {
  return (
    <div
      className={cn(
        'inline-flex items-center rounded-full border border-line bg-surface/70 p-0.5',
        className,
      )}
      role="tablist"
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={active}
            title={option.hint}
            onClick={() => onChange(option.value)}
            className={cn(
              'rounded-full font-medium transition-colors duration-200',
              size === 'sm' ? 'h-7 px-3 text-[12px]' : 'h-8 px-4 text-[13px]',
              active ? 'bg-ink text-paper' : 'text-muted hover:text-ink',
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

interface UnderlineTabsProps<T extends string> {
  options: SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}

/** 下划线 tab：详情页的「目录 / 书评」用，比胶囊更轻。 */
export function UnderlineTabs<T extends string>({
  options,
  value,
  onChange,
  className,
}: UnderlineTabsProps<T>) {
  return (
    <div className={cn('flex items-center gap-7 border-b border-line', className)} role="tablist">
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(option.value)}
            className={cn(
              'relative -mb-px pb-3 text-sm transition-colors',
              active ? 'text-ink' : 'text-muted hover:text-ink-soft',
            )}
          >
            {option.label}
            {option.hint ? (
              <span className="ml-1.5 text-[11px] text-faint">{option.hint}</span>
            ) : null}
            <span
              className={cn(
                'absolute inset-x-0 -bottom-px h-px bg-ink transition-opacity',
                active ? 'opacity-100' : 'opacity-0',
              )}
            />
          </button>
        );
      })}
    </div>
  );
}
