import { createContext, useContext } from 'react';
import type { AuthStatus, LoginRequest, User } from '@/types/auth';

export type AuthContextValue = {
  status: AuthStatus;
  user: User | null;
  error: unknown;
  notice: string | null;
  loggingOut: boolean;
  login(input: LoginRequest): Promise<void>;
  logout(): Promise<void>;
  retry(): void;
};
export const AuthContext = createContext<AuthContextValue | null>(null);
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth requires AuthProvider');
  return context;
}
