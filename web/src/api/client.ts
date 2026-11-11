import type { ApiResult, AuthResult } from './types';

const BASE = import.meta.env.VITE_API_BASE ?? '/api/v1';
const REFRESH_KEY = 'novel.refresh.token';

let accessToken: string | null = null;
let refreshToken: string | null = readRefreshToken();
let refreshing: Promise<string | null> | null = null;

const expiredListeners = new Set<() => void>();

function readRefreshToken(): string | null {
  try {
    return window.localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

export class ApiError extends Error {
  readonly code: number;
  readonly traceId?: string;

  constructor(code: number, message: string, traceId?: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.traceId = traceId;
  }
}

/** 登录失效时通知 AuthProvider 清状态；返回取消订阅函数。 */
export function onSessionExpired(listener: () => void): () => void {
  expiredListeners.add(listener);
  return () => {
    expiredListeners.delete(listener);
  };
}

export function getRefreshToken(): string | null {
  return refreshToken;
}

export function hasRefreshToken(): boolean {
  return Boolean(refreshToken);
}

export function setSession(auth: AuthResult): void {
  accessToken = auth.accessToken;
  refreshToken = auth.refreshToken;
  try {
    window.localStorage.setItem(REFRESH_KEY, auth.refreshToken);
  } catch {
    // 隐私模式下写不进去，本次会话仍然可用
  }
}

export function clearSession(): void {
  const hadSession = Boolean(accessToken || refreshToken);
  accessToken = null;
  refreshToken = null;
  try {
    window.localStorage.removeItem(REFRESH_KEY);
  } catch {
    // ignore
  }
  if (hadSession) {
    expiredListeners.forEach((listener) => listener());
  }
}

async function parseResponse<T>(response: Response): Promise<ApiResult<T>> {
  const text = await response.text();
  try {
    return JSON.parse(text) as ApiResult<T>;
  } catch {
    throw new ApiError(
      response.status,
      text?.slice(0, 200) || `请求失败（HTTP ${response.status}）`,
    );
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** 是否附带 Bearer；登录/注册/刷新要显式关掉 */
  auth?: boolean;
  retryOnUnauthorized?: boolean;
}

async function send<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, retryOnUnauthorized = true } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(-1, '网络连接失败，请确认后端已启动');
  }

  const payload = await parseResponse<T>(response);
  if (payload.code === 0) return payload.data;

  const unauthorized = payload.code === 40100 || payload.code === 10105;
  if (unauthorized && auth && retryOnUnauthorized && refreshToken) {
    const token = await refreshSession();
    if (token) {
      return send<T>(path, { ...options, retryOnUnauthorized: false });
    }
  }
  if (unauthorized) clearSession();

  throw new ApiError(payload.code, payload.message || '请求失败', payload.traceId);
}

/**
 * 单飞刷新：并发 10 个 401 只发一次 refresh。
 *
 * 后端 refresh 是一次性轮换的——第二个请求如果拿同一个旧 jti 再刷，
 * 会直接失败并把刚换到的新令牌一起作废，所以必须排队。
 */
export async function refreshSession(): Promise<string | null> {
  const token = refreshToken;
  if (!token) return null;

  if (!refreshing) {
    refreshing = (async () => {
      try {
        const response = await fetch(`${BASE}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: token }),
        });
        const payload = await parseResponse<AuthResult>(response);
        if (payload.code === 0 && payload.data) {
          setSession(payload.data);
          return payload.data.accessToken;
        }
      } catch {
        // 落到下面统一清会话
      }
      clearSession();
      return null;
    })().finally(() => {
      refreshing = null;
    });
  }

  return refreshing;
}

export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

export function get<T>(path: string): Promise<T> {
  return send<T>(path);
}

export function post<T>(
  path: string,
  body?: unknown,
  options?: Partial<RequestOptions>,
): Promise<T> {
  return send<T>(path, { ...options, method: 'POST', body });
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return send<T>(path, { method: 'PUT', body });
}

export function del<T>(path: string): Promise<T> {
  return send<T>(path, { method: 'DELETE' });
}
