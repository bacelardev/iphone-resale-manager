import { apiRequest } from '@/lib/api/client';
import type {
  BusinessInitialization,
  BusinessInitializationPreview,
  CatalogItem,
  DeviceDetail,
  DevicePhoto,
  DeviceStatus,
  DeviceSummary,
  PageResponse,
  RegisterDeviceInput,
} from '@/types/stage-g';

export type DeviceFilters = {
  search?: string;
  status?: DeviceStatus;
  modelId?: string;
  colorId?: string;
  storageGb?: number;
  purchasedFrom?: string;
  purchasedTo?: string;
  archived?: boolean;
  page?: number;
};

function query(values: Record<string, string | number | boolean | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value));
  });
  const encoded = params.toString();
  return encoded ? `?${encoded}` : '';
}

export function getInitialization() {
  return apiRequest<BusinessInitialization>('/api/v1/business-initialization');
}

export function startInitialization(cutoffAt: string) {
  return apiRequest<BusinessInitialization>('/api/v1/business-initialization/start', {
    method: 'POST',
    body: { cutoffAt },
  });
}

export function updateInitialization(expectedVersion: number, cutoffAt: string) {
  return apiRequest<BusinessInitialization>('/api/v1/business-initialization', {
    method: 'PATCH',
    body: { expectedVersion, cutoffAt },
  });
}

export function getInitializationPreview() {
  return apiRequest<BusinessInitializationPreview>('/api/v1/business-initialization/preview');
}

export function listModels(active?: boolean) {
  return apiRequest<PageResponse<CatalogItem>>(
    `/api/v1/models${query({ active, size: 100, sort: 'displayOrder,asc' })}`,
  );
}

export function listColors(active?: boolean) {
  return apiRequest<PageResponse<CatalogItem>>(
    `/api/v1/colors${query({ active, size: 100, sort: 'name,asc' })}`,
  );
}

export function listParts(active?: boolean, search?: string) {
  return apiRequest<PageResponse<CatalogItem>>(
    `/api/v1/parts${query({ active, search, size: 100, sort: 'name,asc' })}`,
  );
}

export function createModel(input: { code: string; name: string; displayOrder: number }) {
  return apiRequest<CatalogItem>('/api/v1/models', { method: 'POST', body: input });
}

export function createColor(input: { code: string; name: string }) {
  return apiRequest<CatalogItem>('/api/v1/colors', { method: 'POST', body: input });
}

export function createPart(input: { code: string; name: string }) {
  return apiRequest<CatalogItem>('/api/v1/parts', { method: 'POST', body: input });
}

export function updateCatalog(
  kind: 'models' | 'colors' | 'parts',
  item: CatalogItem,
  input: { name: string; displayOrder?: number },
) {
  return apiRequest<CatalogItem>(`/api/v1/${kind}/${item.id}`, {
    method: 'PATCH',
    body: {
      expectedVersion: item.version,
      name: input.name,
      ...(kind === 'models' ? { displayOrder: input.displayOrder } : {}),
    },
  });
}

export function setCatalogActive(
  kind: 'models' | 'colors' | 'parts',
  item: CatalogItem,
  active: boolean,
) {
  return apiRequest<CatalogItem>(
    `/api/v1/${kind}/${item.id}/${active ? 'activate' : 'deactivate'}`,
    {
      method: 'POST',
      body: { expectedVersion: item.version },
    },
  );
}

export function listDevices(filters: DeviceFilters) {
  return apiRequest<PageResponse<DeviceSummary>>(
    `/api/v1/devices${query({ ...filters, size: 24, sort: 'createdAt,desc' })}`,
  );
}

export function getDevice(id: string) {
  return apiRequest<DeviceDetail>(`/api/v1/devices/${id}`);
}

export function registerDevice(
  input: RegisterDeviceInput,
  photos: File[],
  origin: 'operational' | 'initial-import',
) {
  const form = new FormData();
  form.append('device', new Blob([JSON.stringify(input)], { type: 'application/json' }));
  photos.forEach((photo) => form.append('photos', photo));
  const suffix = origin === 'initial-import' ? '/initial-import' : '';
  return apiRequest<DeviceDetail>(`/api/v1/devices${suffix}`, { method: 'POST', body: form });
}

export function updateDevice(id: string, body: Record<string, unknown>) {
  return apiRequest<DeviceDetail>(`/api/v1/devices/${id}`, { method: 'PATCH', body });
}

export function changeDeviceStatus(
  device: DeviceDetail,
  action: 'mark-pending-maintenance' | 'mark-available',
) {
  return apiRequest<DeviceDetail>(`/api/v1/devices/${device.id}/${action}`, {
    method: 'POST',
    body: { expectedVersion: device.version },
  });
}

export function archiveDevice(device: DeviceDetail, reason: string) {
  return apiRequest<DeviceDetail>(`/api/v1/devices/${device.id}/archive`, {
    method: 'POST',
    body: { expectedVersion: device.version, reason },
  });
}

export function addDevicePhoto(deviceId: string, file: File, position: number) {
  const form = new FormData();
  form.append('file', file);
  return apiRequest<DevicePhoto>(`/api/v1/devices/${deviceId}/photos?position=${position}`, {
    method: 'POST',
    body: form,
  });
}

export function removeDevicePhoto(deviceId: string, photoId: string) {
  return apiRequest<void>(`/api/v1/devices/${deviceId}/photos/${photoId}`, { method: 'DELETE' });
}
