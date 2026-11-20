import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, configureApi } from './api';

export interface Session {
  token: string;
  username: string;
  roles: string[];
  expiresAt: number;
}

const KEY = 'parcel.session';

export function decodeJwt(token: string): { sub?: string; roles?: string[]; exp?: number } {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(part))));
  } catch {
    return {};
  }
}

function load(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const s = JSON.parse(raw) as Session;
    return s.expiresAt > Date.now() + 5_000 ? s : null;
  } catch {
    return null;
  }
}

interface AuthValue {
  session: Session | null;
  login: (username: string, password: string) => Promise<Session>;
  logout: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

export class NotOpsError extends Error {}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(load);
  const queryClient = useQueryClient();

  const logout = useCallback(() => {
    queryClient.clear();
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      /* storage blocked */
    }
    setSession(null);
  }, [queryClient]);

  // the API client reads the token lazily, so a refresh never leaves a stale closure behind
  configureApi(() => session?.token ?? null, logout);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.login(username, password);
    const claims = decodeJwt(res.accessToken);
    const roles = claims.roles ?? [];
    if (!roles.includes('OPS')) throw new NotOpsError(roles[0] ?? 'UNKNOWN');
    const s: Session = {
      token: res.accessToken,
      username: claims.sub ?? username,
      roles,
      expiresAt: (claims.exp ?? Date.now() / 1000 + res.expiresIn) * 1000,
    };
    try {
      sessionStorage.setItem(KEY, JSON.stringify(s));
    } catch {
      /* storage blocked: session lives in memory only */
    }
    setSession(s);
    return s;
  }, []);

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
