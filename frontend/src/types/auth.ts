export type User = {
  id: string;
  name: string;
  username: string;
  role: 'SOCIO';
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};
export type LoginRequest = { username: string; password: string };
export type LoginResponse = {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: User;
};
export type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated' | 'error';
