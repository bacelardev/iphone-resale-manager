import type { PageResponse, UserReference } from '@/types/stage-g';

export type FinancialDirection = 'INFLOW' | 'OUTFLOW';
export type FinancialTransactionType =
  | 'OPENING_BALANCE'
  | 'DEVICE_PURCHASE'
  | 'DEVICE_PURCHASE_REVERSAL'
  | 'MAINTENANCE'
  | 'MAINTENANCE_REVERSAL'
  | 'SALE'
  | 'SALE_REVERSAL'
  | 'OWNER_CONTRIBUTION'
  | 'OWNER_WITHDRAWAL'
  | 'MANUAL_ADJUSTMENT';

export type FinancialSource = {
  type: 'DEVICE' | 'MAINTENANCE' | 'SALE';
  id: string;
  deviceId: string;
  reference: string;
};

export type FinancialTransaction = {
  id: string;
  direction: FinancialDirection;
  type: FinancialTransactionType;
  amount: number;
  occurredAt: string;
  description: string | null;
  ownerUser: UserReference | null;
  source: FinancialSource | null;
  reversalOfId: string | null;
  createdAt: string;
  createdBy: UserReference;
};

export type FinancialSummary = {
  period: {
    from: string;
    to: string;
    businessTimezone: string;
  };
  openingBalance: number;
  closingBalance: number;
  revenue: number;
  devicePurchaseCost: number;
  maintenanceCost: number;
  profit: number;
  marginPercent: number | null;
  stockCapital: number;
  calculatedAt: string;
};

export type OwnerCapitalOpening = {
  ownerUser: UserReference;
  historicalContributionAmount: number;
  historicalWithdrawalAmount: number;
};

export type FinancialTransactionPage = PageResponse<FinancialTransaction>;
