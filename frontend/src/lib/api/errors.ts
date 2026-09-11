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
      case 'CONCURRENT_MODIFICATION':
        return 'Este registro mudou em outra operação. Atualize a página e tente novamente.';
      case 'INITIAL_IMPORT_INVALID_DATE':
        return 'A compra deve ser anterior ou igual à data de corte da implantação.';
      case 'INITIALIZATION_CUTOFF_LOCKED':
        return 'A data de corte conflita com importações ou operações já registradas.';
      case 'BUSINESS_INITIALIZATION_NOT_STARTED':
        return 'Inicie a preparação da implantação antes de importar o estoque.';
      case 'BUSINESS_INITIALIZATION_ALREADY_STARTED':
        return 'A preparação da implantação já foi iniciada.';
      case 'PHOTO_MINIMUM_VIOLATION':
        return 'O aparelho deve manter ao menos duas fotos.';
      case 'PHOTO_LIMIT_EXCEEDED':
        return 'O aparelho pode ter no máximo quatro fotos.';
      case 'DEVICE_NOT_AVAILABLE_FOR_SALE':
        return 'O aparelho precisa estar disponível para registrar a venda.';
      case 'SALE_ALREADY_EXISTS':
        return 'Este aparelho já possui uma venda ativa.';
      case 'SALE_DATE_BEFORE_PURCHASE':
        return 'A venda não pode ser anterior à compra do aparelho.';
      case 'SALE_DATE_BEFORE_ACTIVE_MAINTENANCE':
        return 'A venda não pode ser anterior a uma manutenção ativa.';
      case 'SALE_REQUIRES_OPERATIONAL_PERIOD':
        return 'A venda deve ocorrer depois da data de corte da implantação.';
      case 'SALE_NOT_FOUND':
        return 'Este aparelho não possui uma venda ativa.';
      case 'SALE_ALREADY_CANCELLED':
        return 'Esta venda já foi cancelada. Atualize o aparelho.';
      case 'SALE_LEDGER_MISSING':
        return 'A entrada financeira original da venda não foi encontrada.';
      case 'UNSUPPORTED_IMAGE_TYPE':
        return 'Envie uma foto JPEG, PNG ou WebP válida.';
      case 'FILE_TOO_LARGE':
        return 'Cada foto deve ter no máximo 10 MiB.';
      case 'FORBIDDEN':
      case 'CORS_ORIGIN_DENIED':
        return 'O acesso não foi permitido. Contate o responsável pelo sistema.';
    }
  }
  return 'Não foi possível concluir. Tente novamente ou contate o responsável pelo sistema.';
}
