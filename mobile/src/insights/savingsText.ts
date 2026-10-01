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

/**
 * The cheaper purchase it is compared with: "Em 01/08/2026, no Assaí, você pagou só R$ 25,90".
 * The time window is explained once at the top of the screen, not on every row.
 */
export function purchaseReference(purchase: SavingsPurchase): string {
  return (
    `Em ${formatDate(purchase.bestIssuedAt)}, no ${purchase.bestStoreName}, ` +
    `você pagou só ${formatCurrency(purchase.bestUnitPrice)}`
  );
}
