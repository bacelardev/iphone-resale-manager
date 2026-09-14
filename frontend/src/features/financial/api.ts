import { apiRequest } from '@/lib/api/client';
import type {
  FinancialDirection,
  FinancialSummary,
  FinancialTransaction,
  FinancialTransactionPage,
  FinancialTransactionType,
} from '@/types/stage-j';

function transactionQuery(input: {
  from?: string;
  to?: string;
  types?: FinancialTransactionType[];
  directions?: FinancialDirection[];
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  if (input.from) params.set('from', input.from);
  if (input.to) params.set('to', input.to);
  input.types?.forEach((value) => params.append('type', value));
  input.directions?.forEach((value) => params.append('direction', value));
  params.set('page', String(input.page ?? 0));
  params.set('size', String(input.size ?? 20));
  params.set('sort', 'occurredAt,desc');
  return params.toString();
}

export function getFinancialSummary(from?: string, to?: string) {
  const params = new URLSearchParams();
  if (from && to) {
    params.set('from', from);
    params.set('to', to);
  }
  const suffix = params.size ? `?${params}` : '';
  return apiRequest<FinancialSummary>(`/api/v1/financial/summary${suffix}`);
}

export function listFinancialTransactions(input: {
  from?: string;
  to?: string;
  types?: FinancialTransactionType[];
  directions?: FinancialDirection[];
  page?: number;
  size?: number;
}) {
  return apiRequest<FinancialTransactionPage>(
    `/api/v1/financial/transactions?${transactionQuery(input)}`,
  );
}

export function createContribution(input: {
  ownerUserId: string;
  amount: number;
  occurredAt: string;
  description: string;
}) {
  return apiRequest<FinancialTransaction>('/api/v1/financial/contributions', {
    method: 'POST',
    body: input,
  });
}

export function createWithdrawal(input: {
  ownerUserId: string;
  amount: number;
  occurredAt: string;
  description: string;
}) {
  return apiRequest<FinancialTransaction>('/api/v1/financial/withdrawals', {
    method: 'POST',
    body: input,
  });
}

export function createAdjustment(input: {
  direction?: FinancialDirection;
  amount?: number;
  reversalOfTransactionId?: string;
  occurredAt: string;
  description: string;
}) {
  return apiRequest<FinancialTransaction>('/api/v1/financial/adjustments', {
    method: 'POST',
    body: input,
  });
}
