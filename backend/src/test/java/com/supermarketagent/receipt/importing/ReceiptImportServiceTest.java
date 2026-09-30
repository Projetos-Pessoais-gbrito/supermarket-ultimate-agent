package com.supermarketagent.receipt.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.receipt.domain.InvalidAccessKeyException;
import com.supermarketagent.receipt.domain.KeyOnlyLinkException;
import com.supermarketagent.receipt.provider.NfceProviderRegistry;
import com.supermarketagent.receipt.support.FixtureSpProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReceiptImportServiceTest {

    private static final String KEY = FixtureSpProvider.KEY;
    private static final String SP = FixtureSpProvider.SP_URL;
    private static final String QR_URL = FixtureSpProvider.QR_URL;

    @MockitoBean
    private NfceProviderRegistry registry;

    @Autowired
    private ReceiptImportService service;

    @Autowired
    private JdbcTemplate jdbc;

    private final FixtureSpProvider provider = new FixtureSpProvider();
    private long userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, users "
                + "RESTART IDENTITY CASCADE");
        when(registry.providerFor(any())).thenReturn(provider);
        userId = insertUser("ana@example.com");
    }

    @Test
    void importsReceiptWithStoreItemsAndPayments() {
        ReceiptImportResult result = service.importFromQrCode(userId, QR_URL);

        assertThat(result.created()).isTrue();
        assertThat(jdbc.queryForObject("SELECT name FROM stores WHERE cnpj = '11222333000181'", String.class))
                .isEqualTo("SUPERMERCADO EXEMPLO LTDA");
        assertThat(count("receipt_items WHERE receipt_id = " + result.receiptId())).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT total_amount FROM receipts", BigDecimal.class))
                .isEqualByComparingTo("52.92");
        assertThat(jdbc.queryForObject("SELECT method FROM receipt_payments", String.class))
                .isEqualTo("Cartão de Crédito");
    }

    @Test
    void storesIssueTimeAsSaoPauloLocalTime() {
        service.importFromQrCode(userId, QR_URL);

        OffsetDateTime issuedAt = jdbc.queryForObject("SELECT issued_at FROM receipts", OffsetDateTime.class);
        // 15/01/2026 10:30:00 in São Paulo (UTC-3)
        assertThat(issuedAt.toInstant()).isEqualTo(Instant.parse("2026-01-15T13:30:00Z"));
    }

    @Test
    void importingTheSameReceiptTwiceReturnsTheExistingOne() {
        ReceiptImportResult first = service.importFromQrCode(userId, QR_URL);

        ReceiptImportResult second = service.importFromQrCode(userId, QR_URL);

        assertThat(second).isEqualTo(new ReceiptImportResult(first.receiptId(), false));
        assertThat(provider.calls()).isEqualTo(1);
        assertThat(count("receipts")).isEqualTo(1);
    }

    @Test
    void reusesStoreAndStoreProductsAcrossUsers() {
        service.importFromQrCode(userId, QR_URL);
        service.importFromQrCode(insertUser("bia@example.com"), QR_URL);

        assertThat(count("receipts")).isEqualTo(2);
        assertThat(count("stores")).isEqualTo(1);
        // 10 lines, two products bought twice each
        assertThat(count("store_products")).isEqualTo(8);
    }

    @Test
    void doesNotStoreTheBuyerDocumentFromV1QrCodes() {
        String v1Url = SP + "?chNFe=" + KEY + "&nVersao=100&tpAmb=1&cDest=12345678909&vNF=52.92&cHashQRCode=abc123";

        service.importFromQrCode(userId, v1Url);

        assertThat(jdbc.queryForObject("SELECT source_url FROM receipts", String.class))
                .doesNotContain("12345678909");
    }

    @Test
    void rejectsNfeThatIsNotAConsumerReceipt() {
        String nfeUrl = SP + "?p=35260111222333000181550010000000011000000011|2|1|1|abc";

        assertThatThrownBy(() -> service.importFromQrCode(userId, nfeUrl))
                .isInstanceOf(InvalidAccessKeyException.class);
        assertThat(provider.calls()).isZero();
    }

    @Test
    void rejectsKeyOnlyLinksWithoutCallingSefaz() {
        String keyOnly = "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaPublica.aspx?chNFe=" + KEY;

        assertThatThrownBy(() -> service.importFromQrCode(userId, keyOnly))
                .isInstanceOf(KeyOnlyLinkException.class);
        assertThat(provider.calls()).isZero();
    }

    private long insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES (?, 'hash') RETURNING id", Long.class, email);
    }

    private int count(String tableAndFilter) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Integer.class);
    }
}
