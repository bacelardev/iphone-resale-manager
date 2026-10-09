export type UserRole = 'SOCIO';

export type AppUser = {
  id: string;
  name: string;
  username: string;
  role: UserRole;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type UserPage = {
  content: AppUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export const auditActions = [
  'CREATED',
  'UPDATED',
  'ARCHIVED',
  'ACTIVATED',
  'DEACTIVATED',
  'STATUS_CHANGED',
  'PHOTO_ADDED',
  'PHOTO_REMOVED',
  'MAINTENANCE_REGISTERED',
  'MAINTENANCE_CANCELLED',
  'SALE_REGISTERED',
  'SALE_CANCELLED',
  'FINANCIAL_TRANSACTION_CREATED',
] as const;
export type AuditAction = (typeof auditActions)[number];

export const auditedEntityTypes = [
  'USER',
  'IPHONE_MODEL',
  'DEVICE_COLOR',
  'DEVICE',
  'DEVICE_PHOTO',
  'PART_CATALOG',
  'MAINTENANCE',
  'SALE',
  'FINANCIAL_TRANSACTION',
  'BUSINESS_INITIALIZATION',
] as const;
export type AuditedEntityType = (typeof auditedEntityTypes)[number];

export type AuditEvent = {
  id: string;
  occurredAt: string;
  actorUser: { id: string; name: string } | null;
  action: AuditAction;
  entityType: AuditedEntityType;
  entityId: string;
  entityReference: string | null;
  summary: string;
  changes: Record<string, unknown>;
  requestId: string | null;
};

export type AuditPage = {
  content: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
