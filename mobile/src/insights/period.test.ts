import { bestDayWindowDays, parsePeriod, periodDescription } from './period';

jest.mock('expo-secure-store', () => ({}));

describe('period', () => {
  it('describes each period for card subtitles', () => {
    expect(periodDescription(3)).toBe('Últimos 3 meses');
    expect(periodDescription(0)).toBe('Desde a primeira nota');
  });

  it('reads saved values and falls back to 6 months', () => {
    expect(parsePeriod('12')).toBe(12);
    expect(parsePeriod('0')).toBe(0);
    expect(parsePeriod(null)).toBe(6);
    expect(parsePeriod('7')).toBe(6);
    expect(parsePeriod('')).toBe(6);
  });

  it('keeps best-time analysis on at least a year of history', () => {
    expect(bestDayWindowDays(3)).toBe(365);
    expect(bestDayWindowDays(0)).toBe(3650);
  });
});
