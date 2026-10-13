import { afterEach, describe, expect, it, vi } from 'vitest';
import { createUser, listUsers, setUserActive, updateUser } from './api';
import type { AppUser } from '@/types/stage-k';

afterEach(() => vi.unstubAllGlobals());

const user: AppUser = {
  id: '054b3f1f-ec18-4f43-a0c8-aa09a5ac77a4',
  name: 'Sócio',
  username: 'socio',
  role: 'SOCIO',
  active: true,
  createdAt: '2026-10-09T12:00:00Z',
  updatedAt: '2026-10-09T12:00:00Z',
  version: 3,
};

describe('users API contracts', () => {
  it('lists with filters and never sends credentials in the query', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ content: [] }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await listUsers({ search: 'Sócio', active: true, page: 1 });
    const url = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(url.searchParams.get('search')).toBe('Sócio');
    expect(url.searchParams.get('active')).toBe('true');
    expect(url.searchParams.has('password')).toBe(false);
  });

  it('uses the approved create, update and deactivate payloads', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify(user), { status: 200 })),
      );
    vi.stubGlobal('fetch', fetchMock);
    await createUser({ name: 'Novo', username: 'novo', password: 'long-password' });
    await updateUser(user.id, { expectedVersion: 3, name: 'Novo nome' });
    await setUserActive(user, false);

    expect(fetchMock.mock.calls[0]![1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ name: 'Novo', username: 'novo', password: 'long-password' }),
      }),
    );
    expect(fetchMock.mock.calls[1]![1]).toEqual(
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ expectedVersion: 3, name: 'Novo nome' }),
      }),
    );
    expect(fetchMock.mock.calls[2]![0]).toBe(
      `http://localhost:8080/api/v1/users/${user.id}/deactivate`,
    );
    expect(fetchMock.mock.calls[2]![1]).toEqual(
      expect.objectContaining({
        body: JSON.stringify({ expectedVersion: 3 }),
      }),
    );
  });
});
