import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'soft' | 'accent';
type Size = 'sm' | 'md' | 'lg' | 'icon';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-ink text-paper hover:bg-accent-deep active:bg-ink',
  accent: 'bg-accent text-paper hover:bg-accent-deep',
  secondary: 'border border-line-strong bg-transparent text-ink hover:border-ink hover:bg-surface',
  ghost: 'text-ink-soft hover:bg-paper-deep hover:text-ink',
  soft: 'bg-paper-deep text-ink-soft hover:bg-line hover:text-ink',
};

const SIZES: Record<Size, string> = {
  sm: 'h-8 gap-1.5 px-3 text-[13px]',
  md: 'h-10 gap-2 px-4 text-sm',
  lg: 'h-12 gap-2 px-6 text-[15px]',
  icon: 'h-10 w-10',
};

export function buttonClass(variant: Variant = 'primary', size: Size = 'md', className?: string) {
  return cn(
    'inline-flex select-none items-center justify-center whitespace-nowrap rounded-[6px] font-medium',
    'transition-colors duration-200 disabled:pointer-events-none disabled:opacity-40',
    VARIANTS[variant],
    SIZES[size],
    className,
  );
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({ variant = 'primary', size = 'md', className, ...props }: ButtonProps) {
  return <button type="button" className={buttonClass(variant, size, className)} {...props} />;
}

interface ButtonLinkProps {
  to: string;
  variant?: Variant;
  size?: Size;
  className?: string;
  children: ReactNode;
  state?: unknown;
}

export function ButtonLink({ to, variant = 'primary', size = 'md', className, children, state }: ButtonLinkProps) {
  return (
    <Link to={to} state={state} className={buttonClass(variant, size, className)}>
      {children}
    </Link>
  );
}
