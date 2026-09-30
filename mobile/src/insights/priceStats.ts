import type { PricePoint } from '../api/types';

export type PriceStats = {
  last: PricePoint;
  lowest: PricePoint;
  highest: PricePoint;
  average: number;
  /** Last price against the lowest, e.g. 12.5 = paid 12,5% above the best price */
  lastAboveLowestPercent: number;
};

/** Summary of a product's price history (prices arrive oldest first); null without purchases. */
export function priceStats(prices: PricePoint[]): PriceStats | null {
  if (prices.length === 0) {
    return null;
  }
  const last = prices[prices.length - 1];
  // Lowest/highest: earliest occurrence wins on ties, so "melhor preço" points to where it first happened
  const lowest = prices.reduce((best, point) => (point.unitPrice < best.unitPrice ? point : best));
  const highest = prices.reduce((worst, point) => (point.unitPrice > worst.unitPrice ? point : worst));
  const average = prices.reduce((sum, point) => sum + point.unitPrice, 0) / prices.length;
  return {
    last,
    lowest,
    highest,
    average: Math.round(average * 100) / 100,
    lastAboveLowestPercent: Math.round(((last.unitPrice - lowest.unitPrice) / lowest.unitPrice) * 1000) / 10,
  };
}
