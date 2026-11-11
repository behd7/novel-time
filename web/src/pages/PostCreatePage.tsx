import { useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, BookMarked, Loader2, Lock, X } from 'lucide-react';
import type { NovelSummary } from '@/api/types';
import { api } from '@/api/endpoints';
import { useNovels } from '@/api/queries';
import { useAuth } from '@/features/auth/AuthProvider';
import { useToast } from '@/components/ui/Toast';
import { Button, ButtonLink } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadError } from '@/components/ui/LoadError';
import { Skeleton } from '@/components/ui/Skeleton';
import { errorMessage as messageOf } from '@/components/ui/LoadError';

export function PostCreatePage() {
  const { user } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const { data, isLoading, isError, error, refetch } = useNovels({ sort: 'hot', size: 30 });
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [selected, setSelected] = useState<NovelSummary[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  if (!user) {
    return (
      <div className="mx-auto max-w-[900px] px-6 py-20 lg:px-10">
        <EmptyState
          icon={<Lock size={26} strokeWidth={1.2} />}
          title="登录后才能发帖"
          description="社区讨论需要绑定身份，方便别人回复你，也方便删除自己发的内容。"
          action={
            <ButtonLink to="/login" state={{ from: location.pathname }}>
              去登录
            </ButtonLink>
          }
        />
      </div>
    );
  }

  const books = data?.list ?? [];

  const insertRef = (novel: NovelSummary) => {
    if (selected.length >= 5) {
      setFormError('最多引用 5 本小说');
      return;
    }
    if (selected.some((item) => item.id === novel.id)) {
      setFormError('这本已经引用过了');
      return;
    }
    const index = selected.length;
    const token = `[[ref:${index}]]`;
    const element = textareaRef.current;
    const start = element?.selectionStart ?? content.length;
    const end = element?.selectionEnd ?? content.length;
    const next = content.slice(0, start) + token + content.slice(end);
    setContent(next);
    setSelected((current) => [...current, novel]);
    setFormError(null);
    window.requestAnimationFrame(() => {
      element?.focus();
      element?.setSelectionRange(start + token.length, start + token.length);
    });
  };

  const removeRef = (index: number) => {
    let next = content.split(`[[ref:${index}]]`).join('');
    next = next.replace(/\[\[ref:(\d+)\]\]/g, (_, raw: string) => {
      const value = Number(raw);
      return `[[ref:${value > index ? value - 1 : value}]]`;
    });
    setContent(next);
    setSelected((current) => current.filter((_, itemIndex) => itemIndex !== index));
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const nextTitle = title.trim();
    const nextContent = content.trim();
    if (!nextTitle) {
      setFormError('标题不能为空');
      return;
    }
    if (nextTitle.length > 120) {
      setFormError('标题不能超过 120 字');
      return;
    }
    if (!nextContent) {
      setFormError('正文不能为空');
      return;
    }
    if (nextContent.length > 10_000) {
      setFormError('正文不能超过 10000 字');
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const created = await api.createPost({
        title: nextTitle,
        content: nextContent,
        refNovelIds: selected.map((novel) => novel.id),
      });
      toast('帖子已发布');
      navigate(`/posts/${created.id}`);
    } catch (caught) {
      setFormError(messageOf(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-[900px] px-6 pb-24 pt-10 lg:px-10 lg:pt-14">
      <Link
        to="/community"
        className="inline-flex items-center gap-1.5 text-[12px] text-muted transition-colors hover:text-ink"
      >
        <ArrowLeft size={13} />
        返回社区
      </Link>

      <header className="mt-6 border-b border-line pb-7">
        <p className="text-[10.5px] tracking-[0.28em] text-accent">NEW POST</p>
        <h1 className="mt-3 font-serif text-[32px] font-semibold text-ink sm:text-[38px]">
          写帖子
        </h1>
        <p className="mt-2 text-[12.5px] leading-relaxed text-muted">
          可以只写一段话，也可以引用一本书。引用插入后正文会出现占位符，发布时会换成书卡。
        </p>
      </header>

      <form className="mt-8 grid gap-8 lg:grid-cols-[1fr_280px]" onSubmit={submit} noValidate>
        <div>
          <label className="block">
            <span className="text-[11px] tracking-[0.14em] text-faint">标题</span>
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="一句话说清你想聊什么"
              maxLength={120}
              className="mt-2 h-12 w-full border-b border-line-strong bg-transparent font-serif text-[19px] text-ink placeholder:text-faint focus:border-ink focus:outline-none"
            />
          </label>

          <label className="mt-7 block">
            <span className="flex items-center justify-between text-[11px] tracking-[0.14em] text-faint">
              正文
              <span className="normal-case tracking-normal">{content.length} / 10000</span>
            </span>
            <textarea
              ref={textareaRef}
              value={content}
              onChange={(event) => setContent(event.target.value)}
              rows={12}
              maxLength={10_000}
              placeholder="写下你的阅读感受。引用小说后，光标处会出现 [[ref:0]] 这样的占位符。"
              className="mt-2 w-full resize-y rounded-[6px] border border-line bg-surface/50 px-4 py-3 text-[14px] leading-[1.95] text-ink placeholder:text-faint focus:border-line-strong focus:outline-none"
            />
          </label>

          {formError ? (
            <p className="mt-3 rounded-[6px] border border-danger/25 bg-danger-soft px-3 py-2 text-[12px] text-danger">
              {formError}
            </p>
          ) : null}

          <div className="mt-6 flex items-center justify-end gap-2">
            <Button variant="secondary" type="button" onClick={() => navigate(-1)} disabled={submitting}>
              取消
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? <Loader2 size={14} className="animate-spin" /> : null}
              {submitting ? '发布中…' : '发布帖子'}
            </Button>
          </div>
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="rounded-[8px] border border-line bg-surface/50 p-4">
            <p className="flex items-center gap-1.5 text-[10.5px] tracking-[0.16em] text-faint">
              <BookMarked size={12} />
              引用小说（最多 5 本）
            </p>

            {selected.length > 0 ? (
              <ul className="mt-3 space-y-2">
                {selected.map((novel, index) => (
                  <li
                    key={novel.id}
                    className="flex items-center gap-2 rounded-[5px] bg-paper-deep/70 px-2.5 py-2"
                  >
                    <span className="shrink-0 font-serif text-[11px] text-accent">
                      {index}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-[12px] text-ink-soft">
                      {novel.title}
                    </span>
                    <button
                      type="button"
                      onClick={() => removeRef(index)}
                      aria-label={`移除引用 ${novel.title}`}
                      className="shrink-0 text-faint transition-colors hover:text-danger"
                    >
                      <X size={12} />
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-[11.5px] leading-relaxed text-faint">
                还没有引用。选一本后会把占位符插到光标处。
              </p>
            )}

            {isError ? (
              <LoadError className="mt-3 px-2 py-4" error={error} onRetry={() => void refetch()} />
            ) : isLoading ? (
              <div className="mt-3 space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : (
              <select
                aria-label="选择引用的小说"
                value=""
                onChange={(event) => {
                  const novel = books.find((item) => item.id === Number(event.target.value));
                  if (novel) insertRef(novel);
                }}
                className="mt-3 h-9 w-full rounded-[5px] border border-line bg-paper px-2 text-[12px] text-ink-soft focus:border-line-strong focus:outline-none"
              >
                <option value="">选择一本小说…</option>
                {books
                  .filter((novel) => !selected.some((item) => item.id === novel.id))
                  .map((novel) => (
                    <option key={novel.id} value={novel.id}>
                      {novel.title} · {novel.authorName}
                    </option>
                  ))}
              </select>
            )}

            <p className="mt-3 border-t border-line pt-3 text-[10.5px] leading-relaxed text-faint">
              占位符只在发布时替换成书卡；发帖前可以自由编辑正文，但不要删掉引用的编号。
            </p>
          </div>
        </aside>
      </form>
    </div>
  );
}
