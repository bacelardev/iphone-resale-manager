import { apiRequest } from '@/lib/api/client';
import type { AuditAction, AuditEvent, AuditPage, AuditedEntityType } from '@/types/stage-k';

export function listAuditEvents(input: {
  entityType?: AuditedEntityType;
  action?: AuditAction;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams({
    page: String(input.page ?? 0),
    size: String(input.size ?? 20),
    sort: 'occurredAt,desc',
  });
  if (input.entityType) params.set('entityType', input.entityType);
  if (input.action) params.set('action', input.action);
  if (input.from) params.set('from', new Date(`${input.from}T00:00:00-03:00`).toISOString());
  if (input.to) {
    const exclusive = new Date(`${input.to}T00:00:00-03:00`);
    exclusive.setDate(exclusive.getDate() + 1);
    params.set('to', exclusive.toISOString());
  }
  return apiRequest<AuditPage>(`/api/v1/audit?${params}`);
}

export function getAuditEvent(id: string) {
  return apiRequest<AuditEvent>(`/api/v1/audit/${encodeURIComponent(id)}`);
}
