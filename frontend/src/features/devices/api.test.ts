import { AuthTokenStore } from '@/lib/auth/token-store';
import { apiAssetUrl } from '@/lib/api/client';
import { json } from '@/test/fixtures';
import { listDevices, registerDevice } from './api';

describe('Stage G API client', () => {
  it('builds allowlisted device filters without putting credentials in the URL', async () => {
    AuthTokenStore.set('irs_stage-g-test-token');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        json({
          content: [],
          page: 0,
          size: 24,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        }),
      );
    vi.stubGlobal('fetch', fetchMock);
    await listDevices({ search: 'IPH-000001', archived: false });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/api/v1/devices?');
    expect(url).toContain('search=IPH-000001');
    expect(url).not.toContain('irs_stage-g-test-token');
    expect(new Headers(init.headers).get('Authorization')).toBe(
      'Bearer irs_stage-g-test-token',
    );
  });

  it('sends JSON metadata and ordered photos as multipart data', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ id: 'device-id' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const photos = [
      new File(['front'], 'front.webp', { type: 'image/webp' }),
      new File(['back'], 'back.webp', { type: 'image/webp' }),
    ];
    await registerDevice(
      {
        modelId: 'model-id',
        colorId: 'color-id',
        storageGb: 256,
        purchasePrice: 2500,
        purchasedAt: '2026-09-08T12:00:00Z',
        faceIdWorking: true,
        originalScreen: true,
        originalBattery: false,
        batteryHealthPercent: 87,
        initialStatus: 'PENDENTE_MANUTENCAO',
      },
      photos,
      'initial-import',
    );
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://localhost:8080/api/v1/devices/initial-import');
    expect(init.body).toBeInstanceOf(FormData);
    const data = init.body as FormData;
    expect(data.getAll('photos')).toEqual(photos);
    expect(data.get('device')).toBeInstanceOf(Blob);
    expect(new Headers(init.headers).has('Content-Type')).toBe(false);
  });

  it('only resolves signed photo paths from the expected API boundary', () => {
    expect(apiAssetUrl('/api/v1/device-photos/content/id?expires=1&signature=x')).toBe(
      'http://localhost:8080/api/v1/device-photos/content/id?expires=1&signature=x',
    );
    expect(() => apiAssetUrl('https://evil.example/photo')).toThrow(
      'Caminho de mídia inválido',
    );
  });
});
