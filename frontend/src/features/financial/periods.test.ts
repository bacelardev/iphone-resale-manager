import { describe, expect, it } from 'vitest';
import { customPeriod, periodFor } from './periods';

describe('financial periods in America/Bahia', () => {
  it('uses [from,to) for a civil month', () => {
    expect(periodFor('month', new Date('2026-09-14T12:00:00Z'))).toEqual({
      from: '2026-09-01T03:00:00.000Z',
      to: '2026-10-01T03:00:00.000Z',
    });
  });

  it('turns the custom final date into an exclusive next-day boundary', () => {
    expect(customPeriod('2026-09-10', '2026-09-14')).toEqual({
      from: '2026-09-10T03:00:00.000Z',
      to: '2026-09-15T03:00:00.000Z',
    });
  });
});
