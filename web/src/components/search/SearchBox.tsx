import { useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import { useSuggest } from '@/api/queries';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import { cn } from '@/lib/cn';

type Variant = 'header' | 'hero' | 'page';

interface SearchBoxProps {
  variant?: Variant;
  initialValue?: string;
  placeholder?: string;
  autoFocus?: boolean;
  className?: string;
  /** 传了就只回调，不跳转（搜索页里用） */
  onSubmit?: (keyword: string) => void;
}

const VARIANTS: Record<Variant, { form: string; input: string; icon: string }> = {
  header: {
    form: 'h-9 w-44 gap-2 rounded-full border border-line bg-surface/60 px-3 transition-[width,border-color,background-color] duration-300 focus-within:w-60 focus-within:border-line-strong focus-within:bg-surface',
    input: 'text-[13px]',
    icon: 'left-3',
  },
  hero: {
    form: 'h-14 w-full gap-3 rounded-[8px] border border-line bg-surface pl-12 pr-28 shadow-card transition-colors focus-within:border-line-strong',
    input: 'text-[15px]',
    icon: 'left-5',
  },
  page: {
    form: 'h-12 w-full gap-3 rounded-[8px] border border-line bg-surface pl-11 pr-24 transition-colors focus-within:border-line-strong',
    input: 'text-[14px]',
    icon: 'left-4',
  },
};

export function SearchBox({
  variant = 'header',
  initialValue = '',
  placeholder = '搜书名、作者、简介',
  autoFocus = false,
  className,
  onSubmit,
}: SearchBoxProps) {
  const navigate = useNavigate();
  const [value, setValue] = useState(initialValue);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const listId = useId();
  const styles = VARIANTS[variant];

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  const debouncedKeyword = useDebouncedValue(value.trim(), 160);
  const { data: items = [] } = useSuggest(debouncedKeyword);

  const goSearch = (keyword: string) => {
    const q = keyword.trim();
    if (!q) {
      inputRef.current?.focus();
      return;
    }
    setOpen(false);
    if (onSubmit) {
      onSubmit(q);
    } else {
      navigate(`/search?q=${encodeURIComponent(q)}`);
    }
  };

  const goNovel = (id: number) => {
    setOpen(false);
    navigate(`/novel/${id}`);
  };

  const visible = open && items.length > 0;

  return (
    <div className={cn('relative', className)}>
      <form
        className={cn('flex items-center', styles.form)}
        onSubmit={(event) => {
          event.preventDefault();
          if (active >= 0 && items[active]) {
            goNovel(items[active].novelId);
          } else {
            goSearch(value);
          }
        }}
        role="search"
      >
        <Search size={16} className={cn('pointer-events-none absolute text-faint', styles.icon)} />
        <input
          ref={inputRef}
          value={value}
          onChange={(event) => {
            setValue(event.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 120)}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') {
              event.preventDefault();
              if (items.length) setActive((current) => (current + 1) % items.length);
            } else if (event.key === 'ArrowUp') {
              event.preventDefault();
              if (items.length) setActive((current) => (current - 1 + items.length) % items.length);
            } else if (event.key === 'Escape') {
              setOpen(false);
              inputRef.current?.blur();
            }
          }}
          placeholder={placeholder}
          autoFocus={autoFocus}
          className={cn(
            'h-full w-full bg-transparent text-ink placeholder:text-faint focus:outline-none',
            styles.input,
          )}
          role="combobox"
          aria-expanded={visible}
          aria-controls={listId}
          aria-autocomplete="list"
        />
        {variant !== 'header' ? (
          <button
            type="submit"
            className="absolute right-2 top-1/2 h-9 -translate-y-1/2 rounded-[6px] bg-ink px-4 text-[13px] font-medium text-paper transition-colors hover:bg-accent-deep"
          >
            搜索
          </button>
        ) : null}
      </form>

      {visible ? (
        <ul
          id={listId}
          role="listbox"
          className="absolute inset-x-0 top-full z-50 mt-2 overflow-hidden rounded-[8px] border border-line bg-surface p-1.5 shadow-pop"
        >
          <li className="px-2.5 pb-1 pt-1.5 text-[10.5px] tracking-[0.18em] text-faint">
            作品
          </li>
          {items.map((item, index) => (
            <li key={item.novelId} role="option" aria-selected={index === active}>
              <button
                type="button"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => goNovel(item.novelId)}
                onMouseEnter={() => setActive(index)}
                className={cn(
                  'flex w-full items-center justify-between gap-3 rounded-[5px] px-2.5 py-2 text-left transition-colors',
                  index === active ? 'bg-paper-deep' : 'hover:bg-paper-deep/70',
                )}
              >
                <span className="truncate text-[13px] text-ink">{item.title}</span>
                <span className="shrink-0 text-[11px] text-muted">{item.authorName}</span>
              </button>
            </li>
          ))}
          <li className="mt-1 border-t border-line pt-1">
            <button
              type="button"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => goSearch(value)}
              className="flex w-full items-center gap-2 rounded-[5px] px-2.5 py-2 text-left text-[12px] text-muted hover:bg-paper-deep/70"
            >
              <Search size={13} />
              搜索「{value.trim()}」
            </button>
          </li>
        </ul>
      ) : null}
    </div>
  );
}
