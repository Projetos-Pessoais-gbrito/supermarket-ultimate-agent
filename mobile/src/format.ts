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

/** Units of weight or volume: the price on the receipt is per kilo/litre, not what was paid. */
const MEASURE_UNITS = new Set(['KG', 'G', 'L', 'LT', 'ML']);

/** "KG" → true (sold by weight), "UN"/"CX" → false */
export function isSoldByMeasure(unit: string): boolean {
  return MEASURE_UNITS.has(unit.toUpperCase());
}

/** 21.9 "KG" → "R$ 21,90/kg"; 7.59 "UN" → "R$ 7,59" */
export function formatUnitPrice(value: number, unit: string): string {
  return isSoldByMeasure(unit) ? `${formatCurrency(value)}/${unit.toLowerCase()}` : formatCurrency(value);
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

const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const MONTHS_LONG = [
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
];

/** "2026-09" → "set" */
export function formatMonthShort(yearMonth: string): string {
  return MONTHS[Number(yearMonth.slice(5, 7)) - 1];
}

/** "2026-09" → "setembro" */
export function formatMonthLong(yearMonth: string): string {
  return MONTHS_LONG[Number(yearMonth.slice(5, 7)) - 1];
}

/** 12.345 → "12,3%" (absolute value; callers say whether it is up or down) */
export function formatPercent(value: number): string {
  return `${Math.abs(value).toFixed(1).replace('.', ',')}%`;
}
