import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { ChevronDown, LogOut, Menu, Search, X } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthProvider';
import { useToast } from '@/components/ui/Toast';
import { Avatar } from '@/components/ui/Avatar';
import { SearchBox } from '@/components/search/SearchBox';
import { cn } from '@/lib/cn';

const NAV = [
  { to: '/browse', label: '书库' },
  { to: '/rank', label: '榜单' },
];

export function SiteHeader() {
  const { user, logout } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setMenuOpen(false);
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!menuOpen) return;
    const onClick = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [menuOpen]);

  return (
    <header className="sticky top-0 z-40 border-b border-line/80 bg-paper/85 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-[1240px] items-center gap-6 px-6 lg:px-10">
        <Link to="/" className="group flex shrink-0 items-baseline gap-2" aria-label="夜读首页">
          <span className="font-serif text-[23px] font-semibold tracking-[0.14em] text-ink">
            夜读
          </span>
          <span className="hidden text-[10px] tracking-[0.3em] text-faint sm:inline">NOVEL</span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex" aria-label="主导航">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'relative rounded-[5px] px-3 py-2 text-[13.5px] transition-colors',
                  isActive ? 'text-ink' : 'text-muted hover:text-ink',
                )
              }
            >
              {({ isActive }) => (
                <>
                  {item.label}
                  <span
                    className={cn(
                      'absolute inset-x-3 -bottom-[1px] h-px bg-ink transition-opacity',
                      isActive ? 'opacity-100' : 'opacity-0',
                    )}
                  />
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <SearchBox variant="header" className="hidden lg:block" />
          <Link
            to="/search"
            className="inline-flex h-9 w-9 items-center justify-center rounded-full text-muted transition-colors hover:bg-paper-deep hover:text-ink lg:hidden"
            aria-label="搜索"
          >
            <Search size={17} />
          </Link>

          {user ? (
            <div className="relative" ref={menuRef}>
              <button
                type="button"
                onClick={() => setMenuOpen((open) => !open)}
                className="flex h-10 items-center gap-2 rounded-full pl-1 pr-2 transition-colors hover:bg-paper-deep"
                aria-expanded={menuOpen}
                aria-haspopup="menu"
              >
                <Avatar name={user.nickname} avatarKey={user.avatarKey} size={28} />
                <span className="hidden max-w-24 truncate text-[13px] text-ink-soft sm:inline">
                  {user.nickname}
                </span>
                <ChevronDown size={13} className="hidden text-faint sm:block" />
              </button>
              {menuOpen ? (
                <div
                  role="menu"
                  className="absolute right-0 top-full mt-2 w-56 overflow-hidden rounded-[8px] border border-line bg-surface shadow-pop"
                >
                  <div className="border-b border-line px-4 py-3">
                    <p className="truncate text-[13px] font-medium text-ink">{user.nickname}</p>
                    <p className="mt-0.5 truncate text-[11.5px] text-muted">@{user.username}</p>
                  </div>
                  <div className="p-1.5">
                    <p className="px-2.5 py-2 text-[11.5px] text-faint">
                      书架与个人主页在下一阶段开放
                    </p>
                    <button
                      type="button"
                      onClick={() => {
                        logout();
                        toast('已退出登录');
                        navigate('/');
                      }}
                      className="flex w-full items-center gap-2 rounded-[5px] px-2.5 py-2 text-left text-[13px] text-ink-soft transition-colors hover:bg-paper-deep"
                      role="menuitem"
                    >
                      <LogOut size={14} />
                      退出登录
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          ) : (
            <Link
              to="/login"
              state={{ from: location.pathname + location.search }}
              className="inline-flex h-9 items-center rounded-[6px] px-3 text-[13px] font-medium text-ink transition-colors hover:bg-paper-deep"
            >
              登录
            </Link>
          )}

          <button
            type="button"
            onClick={() => setMobileOpen((open) => !open)}
            className="inline-flex h-9 w-9 items-center justify-center rounded-full text-muted transition-colors hover:bg-paper-deep hover:text-ink md:hidden"
            aria-label={mobileOpen ? '关闭菜单' : '打开菜单'}
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X size={18} /> : <Menu size={18} />}
          </button>
        </div>
      </div>

      {mobileOpen ? (
        <div className="border-t border-line bg-paper px-6 py-3 md:hidden">
          <nav className="flex flex-col" aria-label="移动端导航">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    'rounded-[5px] px-2 py-2.5 text-sm',
                    isActive ? 'text-ink' : 'text-muted',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
            {!user ? (
              <Link
                to="/login"
                state={{ from: location.pathname }}
                className="mt-1 rounded-[5px] px-2 py-2.5 text-sm text-accent"
              >
                登录 / 注册
              </Link>
            ) : null}
          </nav>
        </div>
      ) : null}
    </header>
  );
}
