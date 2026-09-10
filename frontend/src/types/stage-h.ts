import type {
  CatalogReference,
  PageResponse,
  RegistrationOrigin,
  UserReference,
} from '@/types/stage-g';

export type MaintenanceStatus = 'ACTIVE' | 'CANCELLED';

export type MaintenanceItemInput = {
  partId: string;
  details?: string | null;
  cost: number;
};

export type MaintenanceItem = {
  id: string;
  part: CatalogReference;
  details: string | null;
  cost: number;
  position: number;
};

export type MaintenanceSummary = {
  id: string;
  deviceId: string;
  internalCode: string;
  performedAt: string;
  responsibleUser: UserReference;
  status: MaintenanceStatus;
  registrationOrigin: RegistrationOrigin;
  total: number;
  cancelledAt: string | null;
  version: number;
};

export type MaintenanceDetail = MaintenanceSummary & {
  items: MaintenanceItem[];
  financialImpact: 'OUTFLOW_CREATED' | 'HISTORICAL_COST_ONLY' | 'NO_FINANCIAL_COST';
  createdAt: string;
  cancelledBy: UserReference | null;
  cancellationReason: string | null;
};

export type MaintenanceFilters = {
  status?: MaintenanceStatus;
  from?: string;
  to?: string;
  page?: number;
};

export type MaintenancePage = PageResponse<MaintenanceSummary>;
