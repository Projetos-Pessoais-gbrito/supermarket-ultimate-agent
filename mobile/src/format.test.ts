import {
  formatCurrency,
  formatUnitPrice,
  isSoldByMeasure,
  formatDate,
  formatDateTime,
  formatMonthLong,
  formatMonthShort,
  formatPercent,
  formatQuantity,
} from './format';

describe('formatCurrency', () => {
  it.each([
    [52.92, 'R$ 52,92'],
    [0.18, 'R$ 0,18'],
    [1234.5, 'R$ 1.234,50'],
    [1234567.891, 'R$ 1.234.567,89'],
    [-3.5, '-R$ 3,50'],
  ])('%p → %p', (value, expected) => {
    expect(formatCurrency(value)).toBe(expected);
  });
});

describe('formatUnitPrice', () => {
  it('says the price is per kilo for items sold by weight', () => {
    expect(formatUnitPrice(21.9, 'KG')).toBe('R$ 21,90/kg');
    expect(isSoldByMeasure('kg')).toBe(true);
  });

  it('shows the plain price for items sold by the unit or package', () => {
    expect(formatUnitPrice(7.59, 'UN')).toBe('R$ 7,59');
    expect(formatUnitPrice(12, 'CX')).toBe('R$ 12,00');
    expect(isSoldByMeasure('UN')).toBe(false);
  });
});

describe('formatQuantity', () => {
  it('uses a decimal comma for weighed items', () => {
    expect(formatQuantity(0.136, 'KG')).toBe('0,136 kg');
  });

  it('shows whole units without decimals', () => {
    expect(formatQuantity(2, 'UN')).toBe('2 un');
  });
});

describe('dates in São Paulo time', () => {
  it('converts UTC instants to local time', () => {
    expect(formatDateTime('2026-01-15T13:30:00Z')).toBe('15/01/2026 10:30');
  });

  it('keeps late-evening purchases on the local day', () => {
    expect(formatDate('2026-09-30T01:30:00Z')).toBe('29/09/2026');
  });
});

describe('months and percentages', () => {
  it('names months in Portuguese', () => {
    expect(formatMonthShort('2026-09')).toBe('set');
    expect(formatMonthLong('2026-03')).toBe('março');
  });

  it('formats percentages with a decimal comma', () => {
    expect(formatPercent(-3.24)).toBe('3,2%');
    expect(formatPercent(12)).toBe('12,0%');
  });
});
