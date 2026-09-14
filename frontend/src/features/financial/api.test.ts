import { afterEach, describe, expect, it, vi } from 'vitest';
import { createOpeningBalance, getFinancialSummary } from './api';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('financial API contracts', () => {
  it('preserves the approved nested summary period and nullable margin', async () => {
    const payload = {
      period: {
        from: '2026-09-01T03:00:00Z',
        to: '2026-10-01T03:00:00Z',
        businessTimezone: 'America/Bahia',
      },
      openingBalance: 0,
      closingBalance: 0,
      revenue: 0,
      devicePurchaseCost: 0,
      maintenanceCost: 0,
      profit: 0,
      marginPercent: null,
      stockCapital: 0,
      calculatedAt: '2026-09-14T12:00:00Z',
    };
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(getFinancialSummary()).resolves.toEqual(payload);
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/financial/summary',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('posts the opening balance contract', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'c82afcb2-df34-4f75-a7e6-0d454e52b6cc',
          type: 'OPENING_BALANCE',
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await createOpeningBalance({
      amount: 4000,
      occurredAt: '2026-09-01T03:00:00Z',
      description: 'Saldo inicial conferido.',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/financial/opening-balance',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          amount: 4000,
          occurredAt: '2026-09-01T03:00:00Z',
          description: 'Saldo inicial conferido.',
        }),
      }),
    );
  });
});
