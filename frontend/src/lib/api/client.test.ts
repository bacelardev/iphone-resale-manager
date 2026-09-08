import { apiRequest, subscribeUnauthorized } from './client';
import { ApiRequestError, NetworkError } from './errors';
import { AuthTokenStore } from '@/lib/auth/token-store';
import { apiFailure, fixtureToken, json } from '@/test/fixtures';

describe('apiRequest', () => {
  it('adds Bearer only when present and a fresh UUID per call', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(json({ ok: true })));
    vi.stubGlobal('fetch', fetchMock);
    await apiRequest('/api/v1/auth/me');
    AuthTokenStore.set(fixtureToken);
    await apiRequest('/api/v1/auth/me');
    const first = fetchMock.mock.calls[0]![1] as RequestInit;
    const second = fetchMock.mock.calls[1]![1] as RequestInit;
    const h1 = new Headers(first.headers);
    const h2 = new Headers(second.headers);
    expect(h1.has('Authorization')).toBe(false);
    expect(h2.get('Authorization')).toBe(`Bearer ${fixtureToken}`);
    expect(h1.get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/);
    expect(h1.get('X-Request-Id')).not.toBe(h2.get('X-Request-Id'));
    expect(second.credentials).toBe('omit');
    expect(second.cache).toBe('no-store');
    expect(fetchMock.mock.calls[1]![0]).not.toContain(fixtureToken);
  });
  it('handles 204 without parsing JSON', async () => {
    const response = new Response(null, { status: 204 });
    const parse = vi.spyOn(response, 'json');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
    await expect(apiRequest('/api/v1/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
    expect(parse).not.toHaveBeenCalled();
  });
  it('keeps the API envelope and Retry-After without deciding by message', async () => {
    const response = apiFailure(429, 'LOGIN_RATE_LIMITED');
    response.headers.set('Retry-After', '60');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
    await expect(apiRequest('/api/v1/auth/login', { authenticated: false })).rejects.toMatchObject({
      detail: { code: 'LOGIN_RATE_LIMITED', status: 429 },
      retryAfter: 60,
    });
  });
  it('notifies global 401 for protected requests but not login', async () => {
    AuthTokenStore.set(fixtureToken);
    const expired = vi.fn();
    const unsubscribe = subscribeUnauthorized(expired);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(apiFailure(401, 'UNAUTHORIZED'))),
    );
    await expect(apiRequest('/api/v1/auth/login', { authenticated: false })).rejects.toBeInstanceOf(
      ApiRequestError,
    );
    expect(expired).not.toHaveBeenCalled();
    await expect(apiRequest('/api/v1/auth/me')).rejects.toBeInstanceOf(ApiRequestError);
    expect(expired).toHaveBeenCalledTimes(1);
    unsubscribe();
  });
  it('does not invalidate a new login when an old response returns 401', async () => {
    AuthTokenStore.set(fixtureToken);
    const expired = vi.fn();
    const unsubscribe = subscribeUnauthorized(expired);
    let finish!: (response: Response) => void;
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(
        () =>
          new Promise<Response>((resolve) => {
            finish = resolve;
          }),
      ),
    );
    const request = apiRequest('/api/v1/auth/me');
    AuthTokenStore.set('different-fixture-token');
    finish(apiFailure(401, 'UNAUTHORIZED'));
    await expect(request).rejects.toBeInstanceOf(ApiRequestError);
    expect(expired).not.toHaveBeenCalled();
    unsubscribe();
  });
  it('distinguishes network failure and preserves the token', async () => {
    AuthTokenStore.set(fixtureToken);
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(apiRequest('/api/v1/auth/me')).rejects.toBeInstanceOf(NetworkError);
    expect(AuthTokenStore.get()).toBe(fixtureToken);
  });
  it('leaves multipart Content-Type to the browser', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const body = new FormData();
    body.set('fixture', 'value');
    await apiRequest('/api/v1/test', { method: 'POST', body });
    const init = fetchMock.mock.calls[0]![1] as RequestInit;
    expect(new Headers(init.headers).has('Content-Type')).toBe(false);
    expect(init.body).toBe(body);
  });
  it('returns a safe fallback for non-JSON failures', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>internal details</html>', { status: 502 })),
    );
    await expect(apiRequest('/api/v1/auth/me')).rejects.toMatchObject({
      detail: { code: 'UNEXPECTED_RESPONSE', status: 502 },
    });
  });
  it('forwards caller cancellation distinctly from a connection failure', async () => {
    const controller = new AbortController();
    controller.abort();
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Cancelled', 'AbortError')));
    await expect(
      apiRequest('/api/v1/auth/me', { signal: controller.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' });
  });
});
