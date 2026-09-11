import type { UserReference } from '@/types/stage-g';

export type SaleStatus = 'ACTIVE' | 'CANCELLED';

export type Sale = {
  id: string;
  deviceId: string;
  salePrice: number;
  soldAt: string;
  responsibleUser: UserReference;
  status: SaleStatus;
  purchasePrice: number;
  maintenanceTotal: number;
  investmentTotal: number;
  profit: number;
  marginPercent: number;
  cancelledAt: string | null;
  cancelledBy: UserReference | null;
  cancellationReason: string | null;
  createdAt: string;
  version: number;
};

export type RegisterSaleInput = {
  deviceVersion: number;
  salePrice: number;
  soldAt: string;
};
