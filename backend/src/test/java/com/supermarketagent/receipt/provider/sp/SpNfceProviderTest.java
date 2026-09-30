package com.supermarketagent.receipt.provider.sp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.supermarketagent.receipt.domain.AccessKey;
import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.SefazUnavailableException;
import com.supermarketagent.receipt.provider.UntrustedReceiptUrlException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SpNfceProviderTest {

    private static final String KEY = "35260111222333000181650010000123451123456788";
    private static final String PATH = "/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx";
    private static final String QR_URL = "https://www.nfce.fazenda.sp.gov.br" + PATH
            + "?p=" + KEY + "|2|1|1|0123456789abcdef0123456789abcdef01234567";
    private static final String EXPECTED_REQUEST = "https://www.nfce.fazenda.sp.gov.br" + PATH
            + "?p=" + KEY + "%7C2%7C1%7C1%7C0123456789abcdef0123456789abcdef01234567";

    private static final String HASH_PARAMS = "|2|1|1|0123456789abcdef0123456789abcdef01234567";
    // What is actually printed on SP receipts; SEFAZ answers it with a 302 to the consultation page
    private static final String SHORT_QR_URL = "https://www.nfce.fazenda.sp.gov.br/qrcode?p=" + KEY + HASH_PARAMS;
    private static final String SHORT_REQUEST = SHORT_QR_URL.replace("|", "%7C");

    private MockRestServiceServer server;
    private SpNfceProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT, "test-agent");
        server = MockRestServiceServer.bindTo(builder).build();
        SpSefazProperties properties = new SpSefazProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO, 3, Duration.ZERO, Duration.ofSeconds(45),
                "test-agent");
        provider = new SpNfceProvider(builder.build(), properties);
    }

    @Test
    void downloadsAndParsesTheReceiptPage() {
        server.expect(requestTo(EXPECTED_REQUEST))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "test-agent"))
                .andRespond(withSuccess(fixture(), MediaType.TEXT_HTML));

        var receipt = provider.fetch(new AccessKey(KEY), QR_URL).receipt();

        assertThat(receipt.items()).hasSize(10);
        assertThat(receipt.totalAmount()).isEqualByComparingTo("52.92");
        server.verify();
    }

    @Test
    void returnsHtmlWithoutBuyerData() {
        String pageWithBuyer = fixture().replace("Consumidor não identificado", "CPF: 123.456.789-09 MARIA DA SILVA");
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withSuccess(pageWithBuyer, MediaType.TEXT_HTML));

        String html = provider.fetch(new AccessKey(KEY), QR_URL).sanitizedHtml();

        assertThat(html).doesNotContain("123.456.789-09", "MARIA DA SILVA").contains("PAO FRANCES");
    }

    @Test
    void followsTheShortLinkPrintedOnReceipts() {
        // SEFAZ-SP sends the pipes unencoded in the Location header
        server.expect(requestTo(SHORT_REQUEST)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "https://www.nfce.fazenda.sp.gov.br" + PATH + "?p=" + KEY + HASH_PARAMS));
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withSuccess(fixture(), MediaType.TEXT_HTML));

        assertThat(provider.fetch(new AccessKey(KEY), SHORT_QR_URL).receipt().items()).hasSize(10);
        server.verify();
    }

    @Test
    void followsRelativeRedirects() {
        server.expect(requestTo(SHORT_REQUEST)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, PATH + "?p=" + KEY + HASH_PARAMS));
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withSuccess(fixture(), MediaType.TEXT_HTML));

        assertThat(provider.fetch(new AccessKey(KEY), SHORT_QR_URL).receipt().items()).hasSize(10);
        server.verify();
    }

    @Test
    void refusesRedirectsOutsideSefazSp() {
        server.expect(requestTo(SHORT_REQUEST)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "https://evil.example.com/steal?p=" + KEY));

        assertThatThrownBy(() -> provider.fetch(new AccessKey(KEY), SHORT_QR_URL))
                .isInstanceOf(UntrustedReceiptUrlException.class);
        server.verify();
    }

    @Test
    void stopsFollowingRedirectLoops() {
        server.expect(ExpectedCount.times(4), requestTo(SHORT_REQUEST))
                .andRespond(withStatus(HttpStatus.FOUND).header(HttpHeaders.LOCATION, SHORT_QR_URL));

        assertThatThrownBy(() -> provider.fetch(new AccessKey(KEY), SHORT_QR_URL))
                .isInstanceOf(NfcePageParseException.class)
                .hasMessageContaining("redirected");
        server.verify();
    }

    @Test
    void upgradesHttpQrCodesToHttps() {
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withSuccess(fixture(), MediaType.TEXT_HTML));

        provider.fetch(new AccessKey(KEY), QR_URL.replace("https://", "http://"));

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://evil.example.com" + PATH + "?p=" + KEY,
        "https://www.nfce.fazenda.sp.gov.br.evil.example.com" + PATH + "?p=" + KEY,
        "https://user@www.nfce.fazenda.sp.gov.br" + PATH + "?p=" + KEY,
        "https://www.nfce.fazenda.sp.gov.br:8443" + PATH + "?p=" + KEY,
        "file:///etc/passwd",
        "https://www.nfce.fazenda.sp.gov.br" + PATH,
    })
    void refusesUrlsOutsideSefazSp(String url) {
        assertThatThrownBy(() -> provider.fetch(new AccessKey(KEY), url))
                .isInstanceOf(UntrustedReceiptUrlException.class);
        server.verify();
    }

    @Test
    void retriesServerErrors() {
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withServerError());
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withSuccess(fixture(), MediaType.TEXT_HTML));

        assertThat(provider.fetch(new AccessKey(KEY), QR_URL).receipt().items()).hasSize(10);
        server.verify();
    }

    @Test
    void givesUpAfterMaxAttempts() {
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withServerError());
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withServerError());
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.fetch(new AccessKey(KEY), QR_URL))
                .isInstanceOf(SefazUnavailableException.class)
                .hasMessageContaining("3 attempts");
        server.verify();
    }

    @Test
    void givesUpWhenTheOverallDeadlinePasses() {
        SpSefazProperties noTime = new SpSefazProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO,
                3, Duration.ZERO, Duration.ofNanos(-1), "test-agent");
        SpNfceProvider impatient = new SpNfceProvider(RestClient.builder().build(), noTime);

        assertThatThrownBy(() -> impatient.fetch(new AccessKey(KEY), QR_URL))
                .isInstanceOf(SefazUnavailableException.class)
                .hasMessageContaining("took longer");
    }

    @Test
    void doesNotRetryClientErrors() {
        server.expect(requestTo(EXPECTED_REQUEST)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> provider.fetch(new AccessKey(KEY), QR_URL))
                .isInstanceOf(SefazUnavailableException.class);
        server.verify();
    }

    @Test
    void rejectsPageOfADifferentReceipt() {
        String otherKey = "35260111222333000181650010000123451123456060";
        server.expect(requestTo(EXPECTED_REQUEST.replace(KEY, otherKey)))
                .andRespond(withSuccess(fixture(), MediaType.TEXT_HTML));

        assertThatThrownBy(() -> provider.fetch(new AccessKey(otherKey), QR_URL.replace(KEY, otherKey)))
                .isInstanceOf(NfcePageParseException.class)
                .hasMessageContaining("different receipt");
    }

    private static String fixture() {
        try (InputStream in = SpNfceProviderTest.class.getResourceAsStream("/fixtures/sp/nfce-10-items-credit-card.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
