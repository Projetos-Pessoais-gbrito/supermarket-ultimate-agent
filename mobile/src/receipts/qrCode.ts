/**
 * Quick client-side check that a scanned QR code is an NFC-e consultation link,
 * so product or payment QR codes get instant feedback without a server round trip.
 * The backend still performs the real validation.
 */
export function isReceiptQrCode(data: string): boolean {
  const value = data.trim();
  return /^https?:\/\/[^\s]+\?/i.test(value) && /[?&](p|chNFe)=\d{44}/i.test(value);
}
