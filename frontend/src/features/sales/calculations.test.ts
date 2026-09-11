import { describe, expect, it } from 'vitest';
import { calculateSalePreview } from './calculations';

describe('calculateSalePreview', () => {
  it('calcula lucro e margem positivos', () => {
    expect(calculateSalePreview(1800, 250, 3200)).toEqual({
      investmentTotal: 2050,
      profit: 1150,
      marginPercent: 35.9375,
    });
  });

  it('aceita lucro zero e prejuízo', () => {
    expect(calculateSalePreview(2000, 500, 2500).profit).toBe(0);
    expect(calculateSalePreview(2000, 500, 2300).profit).toBe(-200);
    expect(calculateSalePreview(2000, 500, 2300).marginPercent).toBeCloseTo(-8.6957, 4);
  });

  it('não calcula margem com preço inválido', () => {
    expect(calculateSalePreview(1000, 0, 0).marginPercent).toBeNull();
  });
});
