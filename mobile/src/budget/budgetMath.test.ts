import type { BudgetLine } from '../api/types';
import { barPercent, budgetStateText, formatMoneyInput, parseMoneyInput } from './budgetMath';

const line = (percentUsed: number, state: BudgetLine['state']): BudgetLine => ({
  category: null,
  label: 'Total do mês',
  limit: 100,
  spent: percentUsed,
  percentUsed,
  projected: percentUsed * 2,
  projectedOver: percentUsed * 2 > 100,
  state,
});

describe('parseMoneyInput', () => {
  it.each([
    ['800', 800],
    ['59,9', 59.9],
    ['1.234,56', 1234.56],
    ['1234,56', 1234.56],
    ['R$ 12,00', 12],
    ['  45 ', 45],
  ])('reads %p as %p', (text, value) => {
    expect(parseMoneyInput(text)).toBe(value);
  });

  it('treats empty text as no limit', () => {
    expect(parseMoneyInput('  ')).toBeNull();
  });

  it.each(['0', '-5', 'abc', '12,345', '1.23', '2000000', '1,2,3'])('rejects %p', text => {
    expect(parseMoneyInput(text)).toBeUndefined();
  });
});

describe('formatMoneyInput', () => {
  it('pre-fills with a decimal comma', () => {
    expect(formatMoneyInput(1234.5)).toBe('1234,50');
    expect(formatMoneyInput(null)).toBe('');
  });

  it('round-trips through parseMoneyInput', () => {
    expect(parseMoneyInput(formatMoneyInput(59.9))).toBe(59.9);
  });
});

describe('barPercent', () => {
  it('caps the bar at 100%', () => {
    expect(barPercent(line(135.2, 'OVER'))).toBe(100);
    expect(barPercent(line(42, 'OK'))).toBe(42);
  });
});

describe('budgetStateText', () => {
  it('describes every state in words', () => {
    expect(budgetStateText(line(40, 'OK'))).toBe('Dentro do limite');
    expect(budgetStateText(line(85, 'WARNING'))).toBe('Perto do limite');
    expect(budgetStateText(line(105, 'OVER'))).toBe('Acima do limite');
  });
});
