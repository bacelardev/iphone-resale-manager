import { AuthTokenStore } from '@/lib/auth/token-store';
import { json } from '@/test/fixtures';
import type { Sale } from '@/types/stage-i';
import { cancelSale, getSale, registerSale } from './api';

const sale: Sale = {
  id: '11111111-1111-4111-8111-111111111111',
  deviceId: '22222222-2222-4222-8222-222222222222',
  salePrice: 3200,
  soldAt: '2026-09-10T18:00:00Z',
  responsibleUser: { id: '33333333-3333-4333-8333-333333333333', name: 'Sócio Teste' },
  status: 'ACTIVE',
  purchasePrice: 1800,
  maintenanceTotal: 250,
  investmentTotal: 2050,
  profit: 1150,
  marginPercent: 35.9375,
  cancelledAt: null,
  cancelledBy: null,
  cancellationReason: null,
  createdAt: '2026-09-10T18:00:01Z',
  version: 0,
};

describe('Stage I sale API client', () => {
  beforeEach(() => AuthTokenStore.set('irs_stage-i-test-token'));

  it('registers and reads the active sale on the singular route', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(sale, 201))
      .mockResolvedValueOnce(json(sale));
    vi.stubGlobal('fetch', fetchMock);
    const input = { deviceVersion: 3, salePrice: 3200, soldAt: sale.soldAt };

    await registerSale(sale.deviceId, input);
    await getSale(sale.deviceId);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`http://localhost:8080/api/v1/devices/${sale.deviceId}/sale`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual(input);
    expect(String(fetchMock.mock.calls[1]?.[0])).toBe(url);
  });

  it('sends both optimistic versions and the cancellation reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ ...sale, status: 'CANCELLED' }));
    vi.stubGlobal('fetch', fetchMock);

    await cancelSale(sale, 4, 'Venda desfeita.');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`http://localhost:8080/api/v1/devices/${sale.deviceId}/sale/cancel`);
    expect(JSON.parse(String(init.body))).toEqual({
      saleVersion: 0,
      deviceVersion: 4,
      reason: 'Venda desfeita.',
    });
  });
});
