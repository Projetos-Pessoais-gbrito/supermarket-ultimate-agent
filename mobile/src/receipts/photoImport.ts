import { isKeyOnlyLink, isReceiptQrCode } from './qrCode';

/** What happened to one picked photo. */
export type PhotoOutcome = 'imported' | 'alreadyImported' | 'noQrCode' | 'notReceipt' | 'keyOnly' | 'failed';

/**
 * The receipt link among the QR codes found in a photo (a photo can also show product or payment
 * codes), preferring full QR code links over key-only ones; null when none is a receipt.
 */
export function pickReceiptQrCode(codes: string[]): string | null {
  const receipts = codes.filter(isReceiptQrCode);
  return receipts.find(code => !isKeyOnlyLink(code)) ?? receipts[0] ?? null;
}

const count = (outcomes: PhotoOutcome[], outcome: PhotoOutcome) => outcomes.filter(o => o === outcome).length;

const plural = (n: number, singular: string, pluralForm: string) => `${n} ${n === 1 ? singular : pluralForm}`;

/** Progress summary, e.g. "3 de 5 importadas, 1 já estava na lista, 1 sem QR code". */
export function photoImportSummary(outcomes: PhotoOutcome[], total = outcomes.length): string {
  const parts = [`${count(outcomes, 'imported')} de ${total} ${total === 1 ? 'importada' : 'importadas'}`];
  const extras: [PhotoOutcome, string, string][] = [
    ['alreadyImported', 'já estava na lista', 'já estavam na lista'],
    ['noQrCode', 'sem QR code', 'sem QR code'],
    ['notReceipt', 'não é de nota fiscal', 'não são de nota fiscal'],
    ['keyOnly', 'precisa do captcha da SEFAZ', 'precisam do captcha da SEFAZ'],
    ['failed', 'com erro', 'com erro'],
  ];
  for (const [outcome, singular, pluralForm] of extras) {
    const n = count(outcomes, outcome);
    if (n > 0) {
      parts.push(plural(n, singular, pluralForm));
    }
  }
  return parts.join(', ');
}
