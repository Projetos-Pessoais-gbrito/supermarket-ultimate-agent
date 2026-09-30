import { photoImportSummary, pickReceiptQrCode } from './photoImport';

const KEY = '35260111222333000181650010000123451123456788';
const QR = `https://www.nfce.fazenda.sp.gov.br/qrcode?p=${KEY}|2|1|1|abc`;
const KEY_ONLY = `https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaPublica.aspx?chNFe=${KEY}`;

describe('pickReceiptQrCode', () => {
  it('ignores other QR codes in the photo', () => {
    expect(pickReceiptQrCode(['https://example.com/pix', QR])).toBe(QR);
  });

  it('prefers full QR code links over key-only ones', () => {
    expect(pickReceiptQrCode([KEY_ONLY, QR])).toBe(QR);
    expect(pickReceiptQrCode([KEY_ONLY])).toBe(KEY_ONLY);
  });

  it('returns null without a receipt code', () => {
    expect(pickReceiptQrCode([])).toBeNull();
    expect(pickReceiptQrCode(['https://example.com/produto/1'])).toBeNull();
  });
});

describe('photoImportSummary', () => {
  it('summarizes a mixed batch', () => {
    expect(photoImportSummary(['imported', 'imported', 'alreadyImported', 'imported', 'noQrCode'])).toBe(
      '3 de 5 importadas, 1 já estava na lista, 1 sem QR code',
    );
  });

  it('pluralizes and lists every problem', () => {
    expect(
      photoImportSummary(['alreadyImported', 'alreadyImported', 'notReceipt', 'keyOnly', 'keyOnly', 'failed']),
    ).toBe(
      '0 de 6 importadas, 2 já estavam na lista, 1 não é de nota fiscal, 2 precisam do captcha da SEFAZ, 1 com erro',
    );
  });

  it('shows progress while photos are still being read', () => {
    expect(photoImportSummary(['imported'], 4)).toBe('1 de 4 importadas');
    expect(photoImportSummary(['imported'])).toBe('1 de 1 importada');
  });
});
