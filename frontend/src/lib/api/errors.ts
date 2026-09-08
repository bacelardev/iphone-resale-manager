import { z } from 'zod';

export const apiErrorSchema = z.object({
  timestamp: z.string(),
  status: z.number(),
  code: z.string(),
  message: z.string(),
  path: z.string(),
  requestId: z.uuid(),
  fieldErrors: z.array(z.object({ field: z.string(), message: z.string() })),
});
export type ApiError = z.infer<typeof apiErrorSchema>;
export class ApiRequestError extends Error {
  constructor(
    public readonly detail: ApiError,
    public readonly retryAfter: number | null = null,
  ) {
    super(detail.code);
  }
}
export class NetworkError extends Error {
  constructor() {
    super('Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.');
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof NetworkError) return error.message;
  if (error instanceof ApiRequestError) {
    switch (error.detail.code) {
      case 'AUTHENTICATION_FAILED':
        return 'Usuário ou senha inválidos.';
      case 'LOGIN_RATE_LIMITED':
        return 'Muitas tentativas. Aguarde um momento antes de tentar novamente.';
      case 'VALIDATION_ERROR':
        return 'Confira os campos informados e tente novamente.';
      case 'UNAUTHORIZED':
        return 'Sua sessão terminou. Entre novamente.';
      case 'FORBIDDEN':
      case 'CORS_ORIGIN_DENIED':
        return 'O acesso não foi permitido. Contate o responsável pelo sistema.';
    }
  }
  return 'Não foi possível concluir. Tente novamente ou contate o responsável pelo sistema.';
}
