import type { SavingsPurchase } from '../api/types';
import { formatWindow, purchasePaid, purchaseReference } from './savingsText';

function purchase(overrides: Partial<SavingsPurchase> = {}): SavingsPurchase {
  return {
    receiptId: 1,
    issuedAt: '2026-09-20T15:00:00Z',
    storeName: 'Carrefour',
    quantity: 1,
    unit: 'UN',
    unitPrice: 28.9,
    extraPaid: 3,
    bestUnitPrice: 25.9,
    bestStoreName: 'Assaí',
    bestIssuedAt: '2026-08-01T15:00:00Z',
    ...overrides,
  };
}

describe('formatWindow', () => {
  it('uses months when the window is a whole number of months', () => {
    expect(formatWindow(60)).toBe('2 meses');
    expect(formatWindow(30)).toBe('1 mês');
  });

  it('falls back to days', () => {
    expect(formatWindow(45)).toBe('45 dias');
  });
});

describe('purchasePaid', () => {
  it('shows only the price for a single unit', () => {
    expect(purchasePaid(purchase())).toBe('Você pagou R$ 28,90');
  });

  it('adds the quantity when more than one unit was bought', () => {
    expect(purchasePaid(purchase({ quantity: 2, unitPrice: 31.4 }))).toBe('Você pagou R$ 31,40 (2 un)');
  });

  it('shows the price per kilo for items sold by weight', () => {
    expect(purchasePaid(purchase({ quantity: 1.115, unit: 'KG', unitPrice: 8.79 }))).toBe(
      'Você pagou R$ 8,79/kg (1,115 kg)',
    );
  });
});

describe('purchaseReference', () => {
  it('says which cheaper purchase it is compared with', () => {
    expect(purchaseReference(purchase(), 60)).toBe(
      'Menor preço até 2 meses antes ou depois: R$ 25,90 no Assaí em 01/08/2026',
    );
  });
});
