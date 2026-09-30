import { isReceiptQrCode } from './qrCode';

const KEY = '35260111222333000181650010000123451123456788';
const SP = 'https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx';

describe('isReceiptQrCode', () => {
  it.each([
    `${SP}?p=${KEY}|2|1|1|abc`,
    `${SP}?p=${KEY}%7C2%7C1%7C1%7Cabc`,
    `http://www.fazenda.pr.gov.br/nfce/qrcode?p=${KEY}|2|1|1|abc`,
    `${SP}?chNFe=${KEY}&nVersao=100`,
    `  ${SP}?p=${KEY}|2|1|1|abc  `,
  ])('accepts %s', url => {
    expect(isReceiptQrCode(url)).toBe(true);
  });

  it.each([
    'https://example.com/produto/123',
    '00020126580014BR.GOV.BCB.PIX0136...', // Pix payment code
    `${SP}?p=123|2|1|1|abc`,
    KEY,
  ])('rejects %s', data => {
    expect(isReceiptQrCode(data)).toBe(false);
  });
});
