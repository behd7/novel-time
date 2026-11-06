import { useState } from 'react';
import { Star } from 'lucide-react';
import { cn } from '@/lib/cn';

interface StarsProps {
  /** 1–5 分，允许小数（4.3 会画成四颗半） */
  value: number;
  size?: number;
  className?: string;
}

/** 只读星级：底层灰星 + 上层按百分比裁切的金星，小数也能准确表达。 */
export function Stars({ value, size = 14, className }: StarsProps) {
  const clamped = Math.max(0, Math.min(5, value));
  return (
    <span
      className={cn('inline-flex items-center gap-[2px]', className)}
      role="img"
      aria-label={`评分 ${clamped.toFixed(1)} 分，满分 5 分`}
    >
      {[0, 1, 2, 3, 4].map((index) => {
        const fill = Math.max(0, Math.min(1, clamped - index));
        return (
          <span key={index} className="relative inline-block" style={{ width: size, height: size }}>
            <Star
              size={size}
              strokeWidth={1.4}
              className="absolute inset-0 text-line-strong"
              aria-hidden
            />
            <span
              className="absolute inset-0 overflow-hidden"
              style={{ width: `${fill * 100}%` }}
              aria-hidden
            >
              <Star size={size} strokeWidth={0} fill="currentColor" className="text-gold" />
            </span>
          </span>
        );
      })}
    </span>
  );
}

interface StarPickerProps {
  value: number | null;
  onChange: (value: number) => void;
  size?: number;
  className?: string;
}

/** 打分：鼠标悬停预览，移开恢复为已选分数。 */
export function StarPicker({ value, onChange, size = 24, className }: StarPickerProps) {
  const [hover, setHover] = useState<number | null>(null);
  const display = hover ?? value ?? 0;

  return (
    <div
      className={cn('inline-flex items-center gap-1', className)}
      onMouseLeave={() => setHover(null)}
      role="radiogroup"
      aria-label="选择评分"
    >
      {[1, 2, 3, 4, 5].map((score) => (
        <button
          key={score}
          type="button"
          role="radio"
          aria-checked={value === score}
          aria-label={`${score} 星`}
          onMouseEnter={() => setHover(score)}
          onFocus={() => setHover(score)}
          onBlur={() => setHover(null)}
          onClick={() => onChange(score)}
          className="rounded-sm p-0.5 transition-transform duration-150 hover:scale-110"
        >
          <Star
            size={size}
            strokeWidth={1.3}
            fill={score <= display ? 'currentColor' : 'none'}
            className={score <= display ? 'text-gold' : 'text-line-strong'}
          />
        </button>
      ))}
    </div>
  );
}
