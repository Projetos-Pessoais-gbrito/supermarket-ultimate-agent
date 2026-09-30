package com.supermarketagent.receipt.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QrCodeUrlParserTest {

    private static final String KEY = "35260111222333000181650010000123451123456788";
    private static final String SP = "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx";

    @ParameterizedTest
    @ValueSource(strings = {
        // v2 online emission
        SP + "?p=" + KEY + "|2|1|1|0123456789abcdef0123456789abcdef01234567",
        // v2 with URL-encoded pipes
        SP + "?p=" + KEY + "%7C2%7C1%7C1%7C0123456789abcdef0123456789abcdef01234567",
        // v2 offline (contingency) emission
        SP + "?p=" + KEY + "|2|1|29|10.00|6b4e4d4d|1|0a1b2c3d4e5f",
        // v1 layout
        SP + "?chNFe=" + KEY + "&nVersao=100&tpAmb=1&cDest=&dhEmi=abc&vNF=10.00",
        // surrounding whitespace from copy/paste
        "  " + SP + "?p=" + KEY + "|2|1|1|abc  ",
    })
    void extractsAccessKeyFromSupportedLayouts(String url) {
        assertThat(QrCodeUrlParser.extractAccessKey(url).value()).isEqualTo(KEY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "not a url with spaces",
        SP,
        SP + "?foo=bar",
        SP + "?p=123|2|1|1|abc",
    })
    void rejectsUrlsWithoutAValidKey(String url) {
        assertThatThrownBy(() -> QrCodeUrlParser.extractAccessKey(url))
                .isInstanceOf(InvalidAccessKeyException.class);
    }
}
