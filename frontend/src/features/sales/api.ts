import { apiRequest } from '@/lib/api/client';
import type { RegisterSaleInput, Sale } from '@/types/stage-i';

export function registerSale(deviceId: string, input: RegisterSaleInput) {
  return apiRequest<Sale>(`/api/v1/devices/${deviceId}/sale`, {
    method: 'POST',
    body: input,
  });
}

export function getSale(deviceId: string) {
  return apiRequest<Sale>(`/api/v1/devices/${deviceId}/sale`);
}

export function cancelSale(sale: Sale, deviceVersion: number, reason: string) {
  return apiRequest<Sale>(`/api/v1/devices/${sale.deviceId}/sale/cancel`, {
    method: 'POST',
    body: { saleVersion: sale.version, deviceVersion, reason },
  });
}
