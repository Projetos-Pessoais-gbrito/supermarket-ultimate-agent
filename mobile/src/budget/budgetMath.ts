import type { BudgetLine } from '../api/types';

/**
 * Reads an amount typed in pt-BR ("1.234,56", "800", "59,9"). Empty text means "no limit" (null);
 * anything that is not a positive amount with up to 2 decimals is `undefined`.
 */
export function parseMoneyInput(text: string): number | null | undefined {
  const trimmed = text.trim().replace(/^R\$\s*/, '');
  if (trimmed === '') {
    return null;
  }
  if (!/^\d{1,3}(\.\d{3})*(,\d{1,2})?$|^\d+(,\d{1,2})?$/.test(trimmed)) {
    return undefined;
  }
  const value = Number(trimmed.replace(/\./g, '').replace(',', '.'));
  return value > 0 && value <= 1_000_000 ? value : undefined;
}

/** 1234.5 → "1234,50", for pre-filling an input */
export function formatMoneyInput(value: number | null | undefined): string {
  return value == null ? '' : value.toFixed(2).replace('.', ',');
}

/** Width of a progress bar, 0–100 */
export function barPercent(line: BudgetLine): number {
  return Math.max(0, Math.min(100, line.percentUsed));
}

/** Status in words, so the state never depends on color alone. */
export function budgetStateText(line: BudgetLine): string {
  switch (line.state) {
    case 'OVER':
      return 'Acima do limite';
    case 'WARNING':
      return 'Perto do limite';
    default:
      return 'Dentro do limite';
  }
}

export function budgetStateIcon(line: BudgetLine): string {
  switch (line.state) {
    case 'OVER':
      return '⛔';
    case 'WARNING':
      return '⚠️';
    default:
      return '✓';
  }
}
