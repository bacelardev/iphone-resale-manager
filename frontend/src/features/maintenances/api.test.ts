import { AuthTokenStore } from '@/lib/auth/token-store';
import { json } from '@/test/fixtures';
import type { MaintenanceDetail } from '@/types/stage-h';
import { cancelMaintenance, getMaintenance, listMaintenances, registerMaintenance } from './api';

const maintenance: MaintenanceDetail = {
  id: '11111111-1111-4111-8111-111111111111',
  deviceId: '22222222-2222-4222-8222-222222222222',
  internalCode: 'MNT-000001',
  performedAt: '2026-09-10T12:00:00Z',
  responsibleUser: {
    id: '33333333-3333-4333-8333-333333333333',
    name: 'Sócio Teste',
  },
  status: 'ACTIVE',
  registrationOrigin: 'OPERATIONAL',
  total: 175.5,
  items: [],
  financialImpact: 'OUTFLOW_CREATED',
  cancelledAt: null,
  cancelledBy: null,
  cancellationReason: null,
  createdAt: '2026-09-10T12:00:00Z',
  version: 3,
};

describe('Stage H maintenance API client', () => {
  beforeEach(() => AuthTokenStore.set('irs_stage-h-test-token'));

  it('builds allowlisted maintenance filters and requests a detail', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json({ content: [], totalElements: 0 }))
      .mockResolvedValueOnce(json(maintenance));
    vi.stubGlobal('fetch', fetchMock);

    await listMaintenances(maintenance.deviceId, {
      status: 'ACTIVE',
      from: '2026-09-01T00:00:00Z',
      to: '2026-09-30T23:59:59Z',
    });
    await getMaintenance(maintenance.deviceId, maintenance.id);

    const listUrl = String(fetchMock.mock.calls[0]?.[0]);
    expect(listUrl).toContain(`/api/v1/devices/${maintenance.deviceId}/maintenances?`);
    expect(listUrl).toContain('status=ACTIVE');
    expect(listUrl).toContain('from=2026-09-01T00%3A00%3A00Z');
    expect(listUrl).not.toContain('irs_stage-h-test-token');
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain(`/maintenances/${maintenance.id}`);
  });

  it.each([
    ['operational', ''],
    ['initial-import', '/initial-import'],
  ] as const)('posts %s maintenance with ordered items', async (origin, suffix) => {
    const fetchMock = vi.fn().mockResolvedValue(json(maintenance, 201));
    vi.stubGlobal('fetch', fetchMock);
    const body = {
      performedAt: '2026-09-10T12:00:00Z',
      items: [
        { partId: '33333333-3333-4333-8333-333333333333', cost: 150 },
        {
          partId: '44444444-4444-4444-8444-444444444444',
          details: 'Limpeza técnica',
          cost: 25.5,
        },
      ],
    };

    await registerMaintenance(maintenance.deviceId, body, origin);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(
      `http://localhost:8080/api/v1/devices/${maintenance.deviceId}/maintenances${suffix}`,
    );
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual(body);
  });

  it('sends optimistic version and reason when cancelling', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ ...maintenance, status: 'CANCELLED' }));
    vi.stubGlobal('fetch', fetchMock);

    await cancelMaintenance(maintenance, 'Lançamento duplicado');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(
      `http://localhost:8080/api/v1/devices/${maintenance.deviceId}/maintenances/${maintenance.id}/cancel`,
    );
    expect(JSON.parse(String(init.body))).toEqual({
      expectedVersion: 3,
      reason: 'Lançamento duplicado',
    });
  });
});
