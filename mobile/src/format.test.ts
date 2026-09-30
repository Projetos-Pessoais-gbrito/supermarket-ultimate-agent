import { formatCurrency, formatDate, formatDateTime, formatQuantity } from './format';

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
