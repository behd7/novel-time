import { Link } from 'react-router-dom';
import { ArrowLeft, Compass } from 'lucide-react';

export function NotFoundPage() {
  return (
    <div className="mx-auto flex max-w-[640px] flex-col items-center px-6 py-28 text-center">
      <p className="font-serif text-[92px] leading-none text-accent/20">404</p>
      <div className="mt-4 flex items-center gap-2 text-faint">
        <Compass size={15} />
        <span className="text-[11px] tracking-[0.24em]">PAGE NOT FOUND</span>
      </div>
      <h1 className="mt-5 font-serif text-[26px] text-ink">这一页被翻过去了</h1>
      <p className="mt-3 max-w-sm text-[13px] leading-[1.9] text-muted">
        链接可能已经失效，或者这本书并不存在。
        回到书库，从头再找一本。
      </p>
      <div className="mt-8 flex items-center gap-3">
        <Link
          to="/browse"
          className="inline-flex h-10 items-center gap-2 rounded-[6px] bg-ink px-5 text-[13px] font-medium text-paper transition-colors hover:bg-accent-deep"
        >
          去书库
        </Link>
        <Link
          to="/"
          className="inline-flex h-10 items-center gap-2 rounded-[6px] border border-line-strong px-5 text-[13px] text-ink transition-colors hover:border-ink"
        >
          <ArrowLeft size={14} />
          回首页
        </Link>
      </div>
    </div>
  );
}
