import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { authApi, setAuthToken, setUnauthorizedHandler } from '../services/api';
import type { UserResponse } from '../types/api';

const TOKEN_KEY = 'syncflow.token';

interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean;
  roles: string[];
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const roles = user?.roles
    ? user.roles.split(',').map((r) => r.trim()).filter(Boolean)
    : [];
  const isAdmin = roles.includes('ADMIN');

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      setLoading(false);
      return;
    }
    setAuthToken(token);
    authApi
      .me()
      .then(setUser)
      .catch(() => {
        // Token invalid/expired — clear it.
        localStorage.removeItem(TOKEN_KEY);
        setAuthToken(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const res = await authApi.login(username, password);
    localStorage.setItem(TOKEN_KEY, res.token);
    setAuthToken(res.token);
    const me = await authApi.me();
    setUser(me);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setAuthToken(null);
    setUser(null);
  }, []);

  // On any 401, the shared axios interceptor triggers this to drop the session.
  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  const refreshUser = useCallback(async () => {
    const me = await authApi.me();
    setUser(me);
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, roles, isAdmin, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
