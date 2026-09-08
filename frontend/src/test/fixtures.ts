import type { User } from '@/types/auth';
export const user: User = {
  id: '054b3f1f-ec18-4f43-a0c8-aa09a5ac77a4',
  name: 'Sócio Teste',
  username: 'socio.teste',
  role: 'SOCIO',
  active: true,
  createdAt: '2026-09-06T12:00:00Z',
  updatedAt: '2026-09-06T12:00:00Z',
  version: 0,
};
export const fixtureToken = 'irs_test-fixture-not-a-real-credential';
export const loginResult = {
  accessToken: fixtureToken,
  tokenType: 'Bearer',
  expiresAt: '2026-09-07T00:00:00Z',
  user,
};
export function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
export function apiFailure(status: number, code: string) {
  return json(
    {
      timestamp: '2026-09-06T12:00:00Z',
      status,
      code,
      message: 'Mensagem controlada pelo servidor',
      path: '/api/v1/auth/login',
      requestId: '054b3f1f-ec18-4f43-a0c8-aa09a5ac77a4',
      fieldErrors: [],
    },
    status,
  );
}
