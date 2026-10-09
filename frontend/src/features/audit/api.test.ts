import { afterEach, describe, expect, it, vi } from 'vitest';
import { getAuditEvent, listAuditEvents } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('audit API contracts', () => {
  it('sends allowlisted filters and exclusive end date', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        }),
        { status: 200 },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await listAuditEvents({
      entityType: 'USER',
      action: 'UPDATED',
      from: '2026-10-01',
      to: '2026-10-09',
      page: 2,
    });

    const url = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(url.pathname).toBe('/api/v1/audit');
    expect(url.searchParams.get('entityType')).toBe('USER');
    expect(url.searchParams.get('action')).toBe('UPDATED');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('sort')).toBe('occurredAt,desc');
    expect(url.searchParams.get('to')).toBe(new Date('2026-10-10T00:00:00-03:00').toISOString());
  });

  it('encodes the detail identifier', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ id: 'abc' }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await getAuditEvent('abc');
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/audit/abc',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});
