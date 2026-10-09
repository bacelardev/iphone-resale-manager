import { apiRequest } from '@/lib/api/client';
import type { AppUser, UserPage } from '@/types/stage-k';

export function listUsers(input: {
  search?: string;
  active?: boolean;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams({
    page: String(input.page ?? 0),
    size: String(input.size ?? 20),
    sort: 'name,asc',
  });
  if (input.search) params.set('search', input.search);
  if (input.active !== undefined) params.set('active', String(input.active));
  return apiRequest<UserPage>(`/api/v1/users?${params}`);
}

export function createUser(input: { name: string; username: string; password: string }) {
  return apiRequest<AppUser>('/api/v1/users', { method: 'POST', body: input });
}

export function updateUser(
  id: string,
  input: { expectedVersion: number; name?: string; username?: string; newPassword?: string },
) {
  return apiRequest<AppUser>(`/api/v1/users/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: input,
  });
}

export function setUserActive(user: AppUser, active: boolean) {
  return apiRequest<AppUser>(
    `/api/v1/users/${encodeURIComponent(user.id)}/${active ? 'activate' : 'deactivate'}`,
    { method: 'POST', body: { expectedVersion: user.version } },
  );
}
