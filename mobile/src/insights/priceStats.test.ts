import type { PricePoint } from '../api/types';
import { priceStats } from './priceStats';

function point(issuedAt: string, storeName: string, unitPrice: number): PricePoint {
  return { issuedAt, storeId: 1, storeName, unitPrice, unit: 'UN' };
}

describe('priceStats', () => {
  it('summarizes last, lowest, highest and average prices', () => {
    const stats = priceStats([
      point('2026-07-10T15:00:00Z', 'ASSAI', 27.9),
      point('2026-08-10T15:00:00Z', 'CARREFOUR', 31.5),
      point('2026-09-10T15:00:00Z', 'ASSAI', 28.9),
    ]);

    expect(stats?.last.unitPrice).toBe(28.9);
    expect(stats?.lowest).toMatchObject({ storeName: 'ASSAI', unitPrice: 27.9 });
    expect(stats?.highest).toMatchObject({ storeName: 'CARREFOUR', unitPrice: 31.5 });
    expect(stats?.average).toBe(29.43);
    expect(stats?.lastAboveLowestPercent).toBe(3.6);
  });

  it('is null without purchases', () => {
    expect(priceStats([])).toBeNull();
  });
});
