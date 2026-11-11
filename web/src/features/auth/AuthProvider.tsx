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
        queryClient.clear();
      }),
    [queryClient],
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

  const logout = useCallback(async () => {
    const token = getRefreshToken();
    try {
      if (token) await api.logout(token);
    } catch {
      // 登出接口是幂等的；网络失败也要把本地会话清掉
    } finally {
      clearSession();
      setUser(null);
      setStatus('anonymous');
      queryClient.clear();
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
    }),
    [user, status, login, register, logout],
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
