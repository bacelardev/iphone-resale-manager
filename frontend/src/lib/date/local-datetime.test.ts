import { nowAsLocalDateTimeValue, toLocalDateTimeValue } from './local-datetime';
describe('local datetime values', () => {
  it('uses local calendar fields instead of UTC serialization', () => {
    const local = new Date(2026, 8, 9, 14, 7, 45);
    expect(toLocalDateTimeValue(local)).toBe('2026-09-09T14:07');
  });
  it('returns the datetime-local shape for the current local time', () => {
    expect(nowAsLocalDateTimeValue()).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });
});
