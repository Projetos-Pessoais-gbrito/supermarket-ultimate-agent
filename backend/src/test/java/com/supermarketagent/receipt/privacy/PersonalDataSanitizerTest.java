package com.supermarketagent.receipt.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.receipt.provider.ParsedReceipt;
import com.supermarketagent.receipt.provider.sp.SpNfcePageParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PersonalDataSanitizerTest {

    private static final String IDENTIFIED_CONSUMER = """
            <li>
              <strong>CPF: </strong>123.456.789-09
            </li>
            <li>
              <strong>Nome: </strong>MARIA DA SILVA
            </li>
            <li>
              <strong>Logradouro: </strong>RUA SECRETA, 42
            </li>""";

    @Test
    void removesConsumerSection() {
        String sanitized = PersonalDataSanitizer.sanitizeHtml(identifiedConsumerPage());

        assertThat(sanitized)
                .doesNotContain("123.456.789-09", "MARIA DA SILVA", "RUA SECRETA")
                .contains(PersonalDataSanitizer.REMOVED);
    }

    @Test
    void masksCpfsOutsideTheConsumerSection() {
        String html = "<html><body><p>CPF do consumidor: 123.456.789-09</p><p>CPF:12345678909</p></body></html>";

        String sanitized = PersonalDataSanitizer.sanitizeHtml(html);

        assertThat(sanitized).doesNotContain("123.456.789-09", "12345678909").contains("***.***.***-**");
    }

    @Test
    void keepsItemCodesAndBarcodes() {
        String html = "<html><body><span class=\"RCod\">(Código: 7891234567895 )</span></body></html>";

        assertThat(PersonalDataSanitizer.sanitizeHtml(html)).contains("7891234567895");
    }

    @Test
    void clearsEncodedViewState() {
        String html = "<input type=\"hidden\" id=\"__VIEWSTATE\" value=\"c2VjcmV0\">"
                + "<input type=\"hidden\" id=\"__EVENTVALIDATION\" value=\"c2VjcmV0\">";

        assertThat(PersonalDataSanitizer.sanitizeHtml(html)).doesNotContain("c2VjcmV0");
    }

    @Test
    void sanitizedPageParsesExactlyLikeTheOriginal() {
        SpNfcePageParser parser = new SpNfcePageParser();
        ParsedReceipt original = parser.parse(identifiedConsumerPage());

        ParsedReceipt sanitized = parser.parse(PersonalDataSanitizer.sanitizeHtml(identifiedConsumerPage()));

        assertThat(sanitized).isEqualTo(original);
    }

    @Test
    void removesBuyerFromV1QrCodeUrl() {
        String url = "https://www.nfce.fazenda.sp.gov.br/qrcode?chNFe=3526&nVersao=100&tpAmb=1&cDest=12345678909&vNF=10.00";

        assertThat(PersonalDataSanitizer.sanitizeQrCodeUrl(url))
                .isEqualTo("https://www.nfce.fazenda.sp.gov.br/qrcode?chNFe=3526&nVersao=100&tpAmb=1&vNF=10.00");
    }

    @Test
    void keepsV2QrCodeUrlUnchanged() {
        String url = "https://www.nfce.fazenda.sp.gov.br/qrcode?p=3526|2|1|1|abc";

        assertThat(PersonalDataSanitizer.sanitizeQrCodeUrl(url)).isEqualTo(url);
    }

    private static String identifiedConsumerPage() {
        try (InputStream in = PersonalDataSanitizerTest.class.getResourceAsStream(
                "/fixtures/sp/nfce-10-items-credit-card.html")) {
            String page = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String withConsumer = page.replace(
                    "<li>\n          <strong>Consumidor não identificado</strong>\n        </li>", IDENTIFIED_CONSUMER);
            assertThat(withConsumer).as("fixture should contain an identified consumer").contains("MARIA DA SILVA");
            return withConsumer;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
