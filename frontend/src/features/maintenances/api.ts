import { apiRequest } from '@/lib/api/client';
import type {
  MaintenanceDetail,
  MaintenanceFilters,
  MaintenanceItemInput,
  MaintenancePage,
} from '@/types/stage-h';

function query(filters: MaintenanceFilters) {
  const params = new URLSearchParams({ size: '20', sort: 'performedAt,desc' });
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value));
  });
  return params.toString();
}

export function listMaintenances(deviceId: string, filters: MaintenanceFilters = {}) {
  return apiRequest<MaintenancePage>(`/api/v1/devices/${deviceId}/maintenances?${query(filters)}`);
}

export function getMaintenance(deviceId: string, maintenanceId: string) {
  return apiRequest<MaintenanceDetail>(`/api/v1/devices/${deviceId}/maintenances/${maintenanceId}`);
}

export function registerMaintenance(
  deviceId: string,
  input: { performedAt: string; items: MaintenanceItemInput[] },
  origin: 'operational' | 'initial-import',
) {
  const suffix = origin === 'initial-import' ? '/initial-import' : '';
  return apiRequest<MaintenanceDetail>(`/api/v1/devices/${deviceId}/maintenances${suffix}`, {
    method: 'POST',
    body: input,
  });
}

export function cancelMaintenance(maintenance: MaintenanceDetail, reason: string) {
  return apiRequest<MaintenanceDetail>(
    `/api/v1/devices/${maintenance.deviceId}/maintenances/${maintenance.id}/cancel`,
    {
      method: 'POST',
      body: { expectedVersion: maintenance.version, reason },
    },
  );
}
