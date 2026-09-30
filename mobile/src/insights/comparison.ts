import type { SpendingInsight } from '../api/types';
import { formatMonthLong, formatPercent } from '../format';

/** Days in a "YYYY-MM" month. */
function daysInMonth(yearMonth: string): number {
  const [year, month] = yearMonth.split('-').map(Number);
  return new Date(year, month, 0).getDate();
}

/**
 * "▲ 16,2% a mais que agosto até o dia 30": the month in progress is compared with the same days of
 * the previous month (the backend computes the numbers); arrow + words so it never relies on color.
 */
export function monthComparisonText(spending: SpendingInsight): string {
  const { previousMonthToDate, comparedUntilDay, changePercent } = spending;
  const month = formatMonthLong(previousMonthToDate.month);
  const period = comparedUntilDay < daysInMonth(previousMonthToDate.month) ? ` até o dia ${comparedUntilDay}` : '';

  if (changePercent === null) {
    return `Sem compras registradas em ${month}${period} para comparar`;
  }
  if (changePercent === 0) {
    return `Igual a ${month}${period}`;
  }
  const direction = changePercent > 0 ? '▲' : '▼';
  const words = changePercent > 0 ? 'a mais' : 'a menos';
  return `${direction} ${formatPercent(changePercent)} ${words} que ${month}${period}`;
}
