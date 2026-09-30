// Hand-rolled pt-BR formatting: Hermes' Intl support varies by platform, and these must match tests exactly.

// Brazil abolished daylight saving time in 2019, so São Paulo is always UTC-3.
const SAO_PAULO_OFFSET_MS = -3 * 60 * 60 * 1000;

function groupThousands(integerPart: string): string {
  return integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** 1234.5 → "R$ 1.234,50" */
export function formatCurrency(value: number): string {
  const [integerPart, decimals] = Math.abs(value).toFixed(2).split('.');
  return `${value < 0 ? '-' : ''}R$ ${groupThousands(integerPart)},${decimals}`;
}

/** 0.136 "KG" → "0,136 kg"; 2 "UN" → "2 un" */
export function formatQuantity(quantity: number, unit: string): string {
  const text = Number.isInteger(quantity) ? String(quantity) : String(quantity).replace('.', ',');
  return `${text} ${unit.toLowerCase()}`;
}

function saoPauloParts(iso: string) {
  const local = new Date(new Date(iso).getTime() + SAO_PAULO_OFFSET_MS);
  const pad = (n: number) => String(n).padStart(2, '0');
  return {
    date: `${pad(local.getUTCDate())}/${pad(local.getUTCMonth() + 1)}/${local.getUTCFullYear()}`,
    time: `${pad(local.getUTCHours())}:${pad(local.getUTCMinutes())}`,
  };
}

/** "2026-01-15T13:30:00Z" → "15/01/2026 10:30" (São Paulo time) */
export function formatDateTime(iso: string): string {
  const { date, time } = saoPauloParts(iso);
  return `${date} ${time}`;
}

/** "2026-01-15T13:30:00Z" → "15/01/2026" (São Paulo date) */
export function formatDate(iso: string): string {
  return saoPauloParts(iso).date;
}
