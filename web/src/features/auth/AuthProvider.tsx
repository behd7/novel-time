import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { User } from '@/api/types';
import { api } from '@/api/endpoints';
import {
  clearSession,
  getRefreshToken,
  hasRefreshToken,
  onSessionExpired,
  refreshSession,
  setSession,
} from '@/api/client';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  user: User | null;
  status: AuthStatus;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<User>;
  register: (username: string, password: string, nickname?: string) => Promise<User>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<User>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 登录态。
 *
 * <p>access 令牌只放内存，refresh 令牌落 localStorage；刷新页面时用 refresh 换一对新令牌，
 * 再调 /users/me 恢复用户。刷新失败就按匿名处理，不阻断阅读。
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');

  useEffect(() => {
    let alive = true;
    (async () => {
      if (!hasRefreshToken()) {
        if (alive) setStatus('anonymous');
        return;
      }
      const access = await refreshSession();
      if (!alive) return;
      if (!access) {
        setStatus('anonymous');
        return;
      }
      try {
        const me = await api.me();
        if (alive) {
          setUser(me);
          setStatus('authenticated');
        }
      } catch {
        clearSession();
        if (alive) setStatus('anonymous');
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  useEffect(
    () =>
      onSessionExpired(() => {
        setUser(null);
        setStatus('anonymous');
        // 不在这里 clear 缓存：清缓存会让仍挂着的观察者立刻重取一次，
        // 而令牌已经清空，控制台会留下一次无意义的 401。私有查询由 enabled 关掉；
        // 下一次登录时 login/register 会统一 clear，不会看到上一个账号的数据。
      }),
    [],
  );

  const login = useCallback(
    async (username: string, password: string) => {
      const auth = await api.login(username, password);
      setSession(auth);
      setUser(auth.user);
      setStatus('authenticated');
      queryClient.clear();
      return auth.user;
    },
    [queryClient],
  );

  const register = useCallback(
    async (username: string, password: string, nickname?: string) => {
      const auth = await api.register(username, password, nickname);
      setSession(auth);
      setUser(auth.user);
      setStatus('authenticated');
      queryClient.clear();
      return auth.user;
    },
    [queryClient],
  );

  const refreshUser = useCallback(async () => {
    const me = await api.me();
    setUser(me);
    setStatus('authenticated');
    return me;
  }, []);

  const logout = useCallback(async () => {
    const token = getRefreshToken();
    try {
      if (token) await api.logout(token);
    } catch {
      // 登出接口是幂等的；网络失败也要把本地会话清掉
    } finally {
      setUser(null);
      setStatus('anonymous');
      clearSession();
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      isAuthenticated: user != null,
      login,
      register,
      logout,
      refreshUser,
    }),
    [user, status, login, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}
