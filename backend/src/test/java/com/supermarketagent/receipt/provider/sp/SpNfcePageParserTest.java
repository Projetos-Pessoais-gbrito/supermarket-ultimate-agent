package com.supermarketagent.receipt.provider.sp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.ParsedReceipt;
import com.supermarketagent.receipt.provider.ParsedReceipt.Item;
import com.supermarketagent.receipt.provider.ParsedReceipt.Payment;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SpNfcePageParserTest {

    // Real SEFAZ-SP page with store, dates, numbers and key anonymized
    private static final String FIXTURE = "/fixtures/sp/nfce-10-items-credit-card.html";

    private final SpNfcePageParser parser = new SpNfcePageParser();

    @Test
    void readsReceiptIdentification() {
        ParsedReceipt receipt = parser.parse(fixture());

        assertThat(receipt.accessKey().value()).isEqualTo("35260111222333000181650010000123451123456788");
        assertThat(receipt.number()).isEqualTo(12345);
        assertThat(receipt.series()).isEqualTo(1);
        assertThat(receipt.issuedAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30, 0));
    }

    @Test
    void readsStore() {
        ParsedReceipt.Store store = parser.parse(fixture()).store();

        assertThat(store.cnpj()).isEqualTo("11222333000181");
        assertThat(store.name()).isEqualTo("SUPERMERCADO EXEMPLO LTDA");
        assertThat(store.address()).isEqualTo("RUA DAS FLORES, 100, CENTRO, SAO PAULO, SP");
    }

    @Test
    void readsItemsSoldByWeightAndByUnit() {
        ParsedReceipt receipt = parser.parse(fixture());

        assertThat(receipt.items()).hasSize(10);
        assertThat(receipt.items().getFirst()).isEqualTo(new Item(
                1, "94794", "PAO FRANCES CONG KG BALCAO",
                new BigDecimal("0.136"), "KG", new BigDecimal("17.99"), new BigDecimal("2.45")));
        assertThat(receipt.items().getLast()).isEqualTo(new Item(
                10, "224654", "CHOC MMS 132G POUCH AO LEITE",
                new BigDecimal("1"), "UN", new BigDecimal("14.99"), new BigDecimal("14.99")));
    }

    @Test
    void readsTotalsTaxesAndPayments() {
        ParsedReceipt receipt = parser.parse(fixture());

        assertThat(receipt.totalAmount()).isEqualByComparingTo("52.92");
        assertThat(receipt.discountAmount()).isEqualByComparingTo("0");
        assertThat(receipt.approximateTaxes()).isEqualByComparingTo("15.27");
        assertThat(receipt.payments()).containsExactly(new Payment("Cartão de Crédito", new BigDecimal("52.92")));
    }

    @Test
    void itemTotalsAddUpToAmountPaid() {
        ParsedReceipt receipt = parser.parse(fixture());

        BigDecimal itemsSum = receipt.items().stream().map(Item::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(itemsSum.subtract(receipt.discountAmount())).isEqualByComparingTo(receipt.totalAmount());
    }

    @Test
    void rejectsPagesWithoutReceipt() {
        assertThatThrownBy(() -> parser.parse("<html><body><div id=\"erro\">Nota não encontrada</div></body></html>"))
                .isInstanceOf(NfcePageParseException.class);
    }

    @Test
    void rejectsCancelledReceipts() {
        String cancelled = fixture().replace("<div id=\"avisos\">", "<input id=\"hdfNotaCancelada\"><div id=\"avisos\">");

        assertThatThrownBy(() -> parser.parse(cancelled))
                .isInstanceOf(NfcePageParseException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void parsesBrazilianNumberFormat() {
        assertThat(SpNfcePageParser.decimal("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(SpNfcePageParser.decimal(" 0,136 ")).isEqualByComparingTo("0.136");
    }

    private static String fixture() {
        try (InputStream in = SpNfcePageParserTest.class.getResourceAsStream(FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
