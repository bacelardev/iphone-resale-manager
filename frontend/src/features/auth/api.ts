import { z } from 'zod';
import { apiRequest } from '@/lib/api/client';
import type { LoginRequest, LoginResponse, User } from '@/types/auth';

const userSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  username: z.string(),
  role: z.literal('SOCIO'),
  active: z.literal(true),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number(),
});
const loginSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal('Bearer'),
  expiresAt: z.string(),
  user: userSchema,
});
// Parse/strip unknown fields instead of putting arbitrary backend objects in state.
export const authApi = {
  async login(input: LoginRequest): Promise<LoginResponse> {
    return loginSchema.parse(
      await apiRequest('/api/v1/auth/login', { method: 'POST', body: input, authenticated: false }),
    );
  },
  async me(signal?: AbortSignal): Promise<User> {
    return userSchema.parse(await apiRequest('/api/v1/auth/me', { signal }));
  },
  logout(): Promise<void> {
    return apiRequest('/api/v1/auth/logout', { method: 'POST' });
  },
};
