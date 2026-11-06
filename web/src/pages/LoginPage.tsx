import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Eye, EyeOff, Loader2 } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthProvider';
import { DEMO_CREDENTIALS, novels, toSummary } from '@/mocks';
import { cn } from '@/lib/cn';
import { NovelCover } from '@/components/book/NovelCover';
import { useToast } from '@/components/ui/Toast';

type Mode = 'login' | 'register';

export function LoginPage() {
  const { user, login, register } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<{ message: string; code?: number } | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (user) navigate(from, { replace: true });
  }, [user, from, navigate]);

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    if (!/^[a-zA-Z0-9_]{4,20}$/.test(username)) {
      next.username = '4–20 位字母、数字或下划线';
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(password)) {
      next.password = '8–64 位，且同时包含字母和数字';
    }
    if (mode === 'register' && nickname.length > 20) {
      next.nickname = '昵称最长 20 个字符';
    }
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!validate()) return;
    setLoading(true);
    try {
      if (mode === 'login') {
        await login(username, password);
        toast('欢迎回来');
      } else {
        await register(username, password, nickname || undefined);
        toast('注册成功，已登录');
      }
      navigate(from, { replace: true });
    } catch (caught) {
      const typed = caught as Error & { code?: number };
      setError({ message: typed.message || '操作失败，请稍后再试', code: typed.code });
    } finally {
      setLoading(false);
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
    setFieldErrors({});
  };

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_0.95fr]">
      <div className="flex flex-col px-6 py-8 sm:px-12 lg:px-16">
        <div className="flex items-center justify-between">
          <Link to="/" className="flex items-baseline gap-2">
            <span className="font-serif text-[22px] font-semibold tracking-[0.14em] text-ink">
              夜读
            </span>
            <span className="text-[10px] tracking-[0.3em] text-faint">NOVEL</span>
          </Link>
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-[12px] text-muted transition-colors hover:text-ink"
          >
            <ArrowLeft size={13} />
            回首页
          </Link>
        </div>

        <div className="mx-auto flex w-full max-w-[380px] flex-1 flex-col justify-center py-14">
          <p className="text-[10.5px] tracking-[0.28em] text-accent">
            {mode === 'login' ? 'WELCOME BACK' : 'JOIN THE LIBRARY'}
          </p>
          <h1 className="mt-3 font-serif text-[34px] font-semibold text-ink">
            {mode === 'login' ? '登录夜读' : '创建账号'}
          </h1>
          <p className="mt-2 text-[12.5px] leading-relaxed text-muted">
            {mode === 'login'
              ? '书架、阅读进度与评分需要登录后同步。'
              : '注册后即可加入书架、评分与写书评。'}
          </p>

          <div className="mt-8 flex items-center gap-6 border-b border-line">
            {(['login', 'register'] as Mode[]).map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => switchMode(item)}
                className={cn(
                  'relative -mb-px pb-3 text-[13.5px] transition-colors',
                  mode === item ? 'text-ink' : 'text-muted hover:text-ink-soft',
                )}
              >
                {item === 'login' ? '登录' : '注册'}
                <span
                  className={cn(
                    'absolute inset-x-0 -bottom-px h-px bg-ink transition-opacity',
                    mode === item ? 'opacity-100' : 'opacity-0',
                  )}
                />
              </button>
            ))}
          </div>

          <form className="mt-7 space-y-5" onSubmit={submit} noValidate>
            <Field
              label="用户名"
              hint={mode === 'register' ? '4–20 位字母、数字或下划线' : undefined}
              error={fieldErrors.username}
            >
              <input
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                placeholder="bookworm"
                className="h-10 w-full bg-transparent text-[14px] text-ink placeholder:text-faint focus:outline-none"
              />
            </Field>

            <Field
              label="密码"
              hint={mode === 'register' ? '至少 8 位，含字母和数字' : undefined}
              error={fieldErrors.password}
            >
              <div className="flex items-center">
                <input
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  type={showPassword ? 'text' : 'password'}
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  placeholder={mode === 'login' ? '输入密码' : '至少 8 位，含字母和数字'}
                  className="h-10 w-full bg-transparent text-[14px] text-ink placeholder:text-faint focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="ml-2 text-faint transition-colors hover:text-ink"
                  aria-label={showPassword ? '隐藏密码' : '显示密码'}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </Field>

            {mode === 'register' ? (
              <Field label="昵称（可选）" error={fieldErrors.nickname}>
                <input
                  value={nickname}
                  onChange={(event) => setNickname(event.target.value)}
                  placeholder="不填则与用户名相同"
                  className="h-10 w-full bg-transparent text-[14px] text-ink placeholder:text-faint focus:outline-none"
                />
              </Field>
            ) : null}

            {error ? (
              <div className="rounded-[6px] border border-danger/25 bg-danger-soft px-3 py-2.5">
                <p className="text-[12px] text-danger">{error.message}</p>
                {error.code ? (
                  <p className="mt-0.5 text-[10.5px] text-danger/60">错误码 {error.code}</p>
                ) : null}
              </div>
            ) : null}

            <button
              type="submit"
              disabled={loading}
              className="mt-2 inline-flex h-11 w-full items-center justify-center gap-2 rounded-[6px] bg-ink text-[14px] font-medium text-paper transition-colors hover:bg-accent-deep disabled:opacity-60"
            >
              {loading ? <Loader2 size={15} className="animate-spin" /> : null}
              {loading ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录'}
              {!loading ? <ArrowRight size={15} /> : null}
            </button>
          </form>

          <div className="mt-8 rounded-[6px] border border-line bg-surface/60 px-4 py-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-[11px] tracking-[0.16em] text-faint">演示账号</p>
                <p className="mt-1 font-mono text-[12px] text-ink-soft">
                  {DEMO_CREDENTIALS.username} / {DEMO_CREDENTIALS.password}
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  switchMode('login');
                  setUsername(DEMO_CREDENTIALS.username);
                  setPassword(DEMO_CREDENTIALS.password);
                }}
                className="shrink-0 text-[11.5px] text-accent transition-colors hover:text-accent-deep"
              >
                一键填入
              </button>
            </div>
            <p className="mt-2 text-[10.5px] leading-relaxed text-faint">
              原型阶段为前端模拟登录；接后端后改为 POST /api/v1/auth/login 与 refresh 轮换。
            </p>
          </div>

          <p className="mt-8 text-[10.5px] leading-relaxed text-faint">
            继续即表示你理解这是演示项目：书目与书评均为虚构，不收集真实个人信息。
          </p>
        </div>
      </div>

      {/* 右侧：深色书墙 */}
      <aside className="relative hidden overflow-hidden bg-ink lg:block">
        <span className="grain opacity-20" aria-hidden />
        <div className="relative flex h-full flex-col justify-between p-14">
          <p className="text-[10px] tracking-[0.32em] text-paper/40">A QUIET PLACE FOR READING</p>

          <div className="flex items-end justify-center gap-5">
            <div className="w-[130px] -rotate-6 opacity-80">
              <NovelCover novel={toSummary(novels[5])} />
            </div>
            <div className="w-[170px] translate-y-4">
              <NovelCover novel={toSummary(novels[0])} />
            </div>
            <div className="w-[130px] rotate-6 opacity-80">
              <NovelCover novel={toSummary(novels[3])} />
            </div>
          </div>

          <div className="max-w-sm">
            <p className="font-serif text-[22px] leading-[1.8] text-paper/85">
              「读书是在别人思想的帮助下，
              <br />
              建立起自己的思想。」
            </p>
            <p className="mt-4 text-[11px] tracking-[0.2em] text-paper/35">尼古拉·鲁巴金</p>
          </div>
        </div>
      </aside>
    </div>
  );
}

function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="flex items-baseline justify-between">
        <span className="text-[11.5px] tracking-[0.08em] text-muted">{label}</span>
        {hint ? <span className="text-[10.5px] text-faint">{hint}</span> : null}
      </span>
      <div
        className={cn(
          'mt-1.5 border-b transition-colors focus-within:border-ink',
          error ? 'border-danger' : 'border-line-strong',
        )}
      >
        {children}
      </div>
      {error ? <span className="mt-1 block text-[10.5px] text-danger">{error}</span> : null}
    </label>
  );
}
