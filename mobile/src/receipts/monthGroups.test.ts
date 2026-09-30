import type { ReceiptSummary } from '../api/types';
import { groupByMonth, monthChipLabel, monthTitle, saoPauloMonth } from './monthGroups';

const receipt = (id: number, issuedAt: string): ReceiptSummary => ({
  id,
  storeName: 'Assaí',
  issuedAt,
  totalAmount: 10,
  itemCount: 1,
});

describe('saoPauloMonth', () => {
  it('uses São Paulo time at month boundaries', () => {
    expect(saoPauloMonth('2026-10-01T01:30:00Z')).toBe('2026-09');
    expect(saoPauloMonth('2026-10-01T03:00:00Z')).toBe('2026-10');
  });
});

describe('month labels', () => {
  it('titles a month header', () => {
    expect(monthTitle('2026-03')).toBe('Março de 2026');
  });

  it('shortens a month for a chip', () => {
    expect(monthChipLabel('2026-09')).toBe('set/2026');
  });
});

describe('groupByMonth', () => {
  it('puts a header with the server total before each month', () => {
    const rows = groupByMonth(
      [receipt(3, '2026-09-20T15:00:00Z'), receipt(2, '2026-09-05T15:00:00Z'), receipt(1, '2026-08-10T15:00:00Z')],
      [
        { month: '2026-09', total: 42.5, receiptCount: 2 },
        { month: '2026-08', total: 20, receiptCount: 1 },
      ],
    );

    expect(rows.map(row => row.key)).toEqual([
      'month-2026-09',
      'receipt-3',
      'receipt-2',
      'month-2026-08',
      'receipt-1',
    ]);
    expect(rows[0]).toMatchObject({ type: 'month', total: 42.5, receiptCount: 2 });
  });

  it('leaves the total empty when the server did not send it', () => {
    expect(groupByMonth([receipt(1, '2026-08-10T15:00:00Z')], [])[0]).toMatchObject({
      type: 'month',
      total: null,
    });
  });

  it('returns no rows for no receipts', () => {
    expect(groupByMonth([], [])).toEqual([]);
  });
});
