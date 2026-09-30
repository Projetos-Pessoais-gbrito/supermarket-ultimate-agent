import type { SpendingInsight } from '../api/types';
import { monthComparisonText } from './comparison';

function insight(changePercent: number | null, comparedUntilDay: number, previous = '2026-08'): SpendingInsight {
  const month = { month: previous, total: 100, receiptCount: changePercent === null ? 0 : 2 };
  return {
    currentMonth: { month: '2026-09', total: 116.2, receiptCount: 9 },
    previousMonth: month,
    previousMonthToDate: month,
    comparedUntilDay,
    changePercent,
    monthly: [],
    byStore: [],
    byCategory: [],
  };
}

describe('monthComparisonText', () => {
  it('says which days were compared for a month in progress', () => {
    expect(monthComparisonText(insight(16.2, 30))).toBe('▲ 16,2% a mais que agosto até o dia 30');
  });

  it('omits the day when the whole previous month was compared', () => {
    expect(monthComparisonText(insight(-5, 31))).toBe('▼ 5,0% a menos que agosto');
    expect(monthComparisonText(insight(-5, 28, '2026-02'))).toBe('▼ 5,0% a menos que fevereiro');
  });

  it('explains when there is nothing to compare', () => {
    expect(monthComparisonText(insight(null, 10))).toBe('Sem compras registradas em agosto até o dia 10 para comparar');
  });

  it('handles no change', () => {
    expect(monthComparisonText(insight(0, 15))).toBe('Igual a agosto até o dia 15');
  });
});
