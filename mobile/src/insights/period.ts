import * as SecureStore from 'expo-secure-store';

/** Dashboard period in months; 0 = "Tudo" (since the first receipt, as the backend defines it). */
export type Period = 3 | 6 | 12 | 0;

export const PERIODS: { value: Period; label: string }[] = [
  { value: 3, label: '3 meses' },
  { value: 6, label: '6 meses' },
  { value: 12, label: '12 meses' },
  { value: 0, label: 'Tudo' },
];

export const DEFAULT_PERIOD: Period = 6;

const PREFERENCE_KEY = 'prefs.dashboardPeriod';

/** Card subtitle for the period, e.g. "Últimos 6 meses" or "Desde a primeira nota". */
export function periodDescription(period: Period): string {
  return period === 0 ? 'Desde a primeira nota' : `Últimos ${period} meses`;
}

/** Best-time analysis needs a long history to be reliable: at least 12 months, or everything. */
export function bestDayWindowDays(period: Period): number {
  return period === 0 ? 3650 : 365;
}

export function parsePeriod(value: string | null | undefined): Period {
  const number = Number(value);
  return PERIODS.some(period => period.value === number) && value !== null && value !== ''
    ? (number as Period)
    : DEFAULT_PERIOD;
}

export async function loadPeriod(): Promise<Period> {
  try {
    return parsePeriod(await SecureStore.getItemAsync(PREFERENCE_KEY));
  } catch {
    return DEFAULT_PERIOD;
  }
}

export function savePeriod(period: Period): Promise<void> {
  return SecureStore.setItemAsync(PREFERENCE_KEY, String(period)).catch(() => undefined);
}
