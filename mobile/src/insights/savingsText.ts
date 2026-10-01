import type { SavingsPurchase } from '../api/types';
import { formatCurrency, formatDate, formatQuantity } from '../format';

/** 60 → "2 meses", 30 → "1 mês", 45 → "45 dias" */
export function formatWindow(days: number): string {
  if (days % 30 !== 0) {
    return `${days} dias`;
  }
  const months = days / 30;
  return months === 1 ? '1 mês' : `${months} meses`;
}

/** "R$ 28,90", "R$ 31,40 (2 un)", "R$ 8,79/kg (1,115 kg)" */
export function purchasePaid(purchase: SavingsPurchase): string {
  const perUnit = purchase.unit.toUpperCase() === 'UN' ? '' : `/${purchase.unit.toLowerCase()}`;
  const single = purchase.unit.toUpperCase() === 'UN' && purchase.quantity === 1;
  const quantity = single ? '' : ` (${formatQuantity(purchase.quantity, purchase.unit)})`;
  return `Você pagou ${formatCurrency(purchase.unitPrice)}${perUnit}${quantity}`;
}

/** "Menor preço até 2 meses antes ou depois: R$ 25,90 no Assaí em 01/08/2026" */
export function purchaseReference(purchase: SavingsPurchase, windowDays: number): string {
  return (
    `Menor preço até ${formatWindow(windowDays)} antes ou depois: ` +
    `${formatCurrency(purchase.bestUnitPrice)} no ${purchase.bestStoreName} em ${formatDate(purchase.bestIssuedAt)}`
  );
}
