import { isSefazSpPage } from './sefazPage';

describe('isSefazSpPage', () => {
  it.each([
    'https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaPublica.aspx?chNFe=1',
    'http://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaResumida.aspx',
    'https://nfce.fazenda.sp.gov.br/qrcode?p=1',
    'https://portal.fazenda.sp.gov.br/',
    'about:blank',
  ])('allows %s', url => {
    expect(isSefazSpPage(url)).toBe(true);
  });

  it.each([
    'https://evil.example.com/?next=fazenda.sp.gov.br',
    'https://fazenda.sp.gov.br.evil.com/',
    'javascript:alert(1)',
  ])('refuses %s', url => {
    expect(isSefazSpPage(url)).toBe(false);
  });
});
