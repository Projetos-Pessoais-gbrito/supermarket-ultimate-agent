/**
 * Quick client-side check that a scanned QR code is an NFC-e consultation link,
 * so product or payment QR codes get instant feedback without a server round trip.
 * The backend still performs the real validation.
 */
export function isReceiptQrCode(data: string): boolean {
  const value = data.trim();
  return /^https?:\/\/[^\s]+\?/i.test(value) && /[?&](p|chNFe)=\d{44}/i.test(value);
}

/**
 * Links with only the access key (e.g. SEFAZ "consulta por chave": ConsultaPublica.aspx?chNFe=...)
 * need a captcha on the SEFAZ site. QR code links carry a version and a verification hash.
 */
export function isKeyOnlyLink(data: string): boolean {
  const value = data.trim();
  const p = /[?&]p=([^&]*)/i.exec(value);
  if (p) {
    return !decodeURIComponent(p[1]).includes('|');
  }
  return /[?&]chNFe=\d{44}/i.test(value) && !/[?&]cHashQRCode=/i.test(value);
}
