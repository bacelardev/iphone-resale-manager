import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { subscribeUnauthorized } from '@/lib/api/client';
import { AuthTokenStore } from '@/lib/auth/token-store';
import type { AuthStatus, LoginRequest, User } from '@/types/auth';
import { authApi } from './api';
import { AuthContext } from './context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const generation = useRef(0);
  const [status, setStatus] = useState<AuthStatus>('checking');
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const invalidatePending = useCallback(() => {
    generation.current++;
  }, []);

  const clearSession = useCallback(
    (message: string | null = null) => {
      generation.current++;
      AuthTokenStore.clear();
      void queryClient.cancelQueries();
      queryClient.clear();
      setUser(null);
      setError(null);
      setNotice(message);
      setStatus('unauthenticated');
    },
    [queryClient],
  );

  const restore = useCallback(async () => {
    const epoch = ++generation.current;
    if (!AuthTokenStore.get()) {
      clearSession();
      return;
    }
    setStatus('checking');
    setError(null);
    try {
      const restored = await queryClient.fetchQuery({
        queryKey: ['private', 'me', epoch],
        queryFn: ({ signal }) => authApi.me(signal),
        staleTime: 0,
        retry: false,
      });
      if (epoch !== generation.current) return;
      setUser(restored);
      setStatus('authenticated');
    } catch (failure) {
      if (epoch !== generation.current) return;
      setError(failure);
      setStatus('error'); // Network errors retain the token and offer retry.
    }
  }, [queryClient, clearSession]);

  useEffect(() => {
    const unsubscribe = subscribeUnauthorized(() =>
      clearSession('Sua sessão terminou. Entre novamente.'),
    );
    void restore();
    return () => {
      invalidatePending();
      unsubscribe();
      void queryClient.cancelQueries();
    };
  }, [clearSession, restore, queryClient, invalidatePending]);

  async function login(input: LoginRequest) {
    const epoch = ++generation.current;
    setNotice(null);
    const result = await authApi.login(input);
    if (epoch !== generation.current) return;
    AuthTokenStore.set(result.accessToken);
    void queryClient.cancelQueries();
    queryClient.clear();
    setUser(result.user);
    setError(null);
    setStatus('authenticated');
  }

  async function logout() {
    if (loggingOut) return;
    setLoggingOut(true);
    let warning: string | null = null;
    try {
      if (AuthTokenStore.get()) await authApi.logout();
    } catch {
      warning =
        'Você saiu desta aba. Não foi possível confirmar o encerramento no servidor; a sessão remota pode continuar válida até expirar.';
    } finally {
      clearSession(warning);
      setLoggingOut(false);
    }
  }

  return (
    <AuthContext
      value={{
        status,
        user,
        error,
        notice,
        loggingOut,
        login,
        logout,
        retry: () => {
          void restore();
        },
      }}
    >
      {children}
    </AuthContext>
  );
}
