import { AuthTokenStore } from '@/lib/auth/token-store';
import { apiErrorSchema, ApiRequestError, NetworkError } from './errors';

const base = new URL(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080');
if (
  !['http:', 'https:'].includes(base.protocol) ||
  base.username ||
  base.password ||
  base.search ||
  base.hash ||
  base.pathname !== '/'
) {
  throw new Error('VITE_API_BASE_URL deve conter somente uma origem HTTP(S).');
}

export function apiAssetUrl(path: string): string {
  if (!path.startsWith('/api/v1/device-photos/content/')) {
    throw new Error('Caminho de mídia inválido.');
  }
  return new URL(path, base.origin).toString();
}
if (
  import.meta.env.PROD &&
  base.protocol !== 'https:' &&
  !['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)
) {
  throw new Error('A API de produção deve usar HTTPS.');
}

let onUnauthorized: (() => void) | undefined;
export function subscribeUnauthorized(handler: () => void): () => void {
  onUnauthorized = handler;
  return () => {
    if (onUnauthorized === handler) onUnauthorized = undefined;
  };
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  authenticated?: boolean;
};

export async function apiRequest<T>(
  path: `/api/v1/${string}`,
  options: RequestOptions = {},
): Promise<T> {
  if (!path.startsWith('/api/v1/') || path.includes('#')) throw new Error('Rota de API inválida.');
  const requestId = crypto.randomUUID();
  const token = options.authenticated === false ? null : AuthTokenStore.get();
  const headers = new Headers({ Accept: 'application/json', 'X-Request-Id': requestId });
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const multipart = options.body instanceof FormData;
  if (options.body !== undefined && !multipart) headers.set('Content-Type', 'application/json');
  const controller = new AbortController();
  const abort = () => controller.abort();
  options.signal?.addEventListener('abort', abort, { once: true });
  if (options.signal?.aborted) controller.abort();
  const timer = setTimeout(abort, 15_000);
  try {
    const response = await fetch(`${base.origin}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body:
        options.body === undefined
          ? undefined
          : multipart
            ? (options.body as FormData)
            : JSON.stringify(options.body),
      signal: controller.signal,
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
      referrerPolicy: 'no-referrer',
    });
    // An old request must not invalidate a more recent login. Login 401 is local to the form.
    if (
      response.status === 401 &&
      options.authenticated !== false &&
      token === AuthTokenStore.get()
    ) {
      onUnauthorized?.();
    }
    if (response.status === 204) return undefined as T;
    let payload: unknown;
    try {
      payload = await response.json();
    } catch {
      payload = undefined;
    }
    if (!response.ok) {
      const parsed = apiErrorSchema.safeParse(payload);
      const detail = parsed.success
        ? { ...parsed.data, status: response.status }
        : {
            timestamp: new Date().toISOString(),
            status: response.status,
            code: response.status === 401 ? 'UNAUTHORIZED' : 'UNEXPECTED_RESPONSE',
            message: 'Resposta inesperada do servidor.',
            path: path.split('?')[0]!,
            requestId,
            fieldErrors: [],
          };
      const retry = response.headers.get('Retry-After');
      const seconds = retry === null ? NaN : Number(retry);
      throw new ApiRequestError(detail, Number.isFinite(seconds) && seconds >= 0 ? seconds : null);
    }
    if (payload === undefined)
      throw new ApiRequestError({
        timestamp: new Date().toISOString(),
        status: response.status,
        code: 'UNEXPECTED_RESPONSE',
        message: 'Resposta inesperada do servidor.',
        path,
        requestId,
        fieldErrors: [],
      });
    return payload as T;
  } catch (error) {
    if (options.signal?.aborted) throw new DOMException('Request cancelled', 'AbortError');
    if (error instanceof ApiRequestError) throw error;
    throw new NetworkError();
  } finally {
    clearTimeout(timer);
    options.signal?.removeEventListener('abort', abort);
  }
}
