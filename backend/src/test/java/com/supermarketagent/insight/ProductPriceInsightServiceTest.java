package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.ProductPriceInsight.PricePoint;
import com.supermarketagent.insight.ProductPriceInsight.ProductSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductPriceInsightServiceTest {

    @Autowired
    private ProductPriceInsightService service;

    @Autowired
    private JdbcTemplate jdbc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long carrefour;
    private long rice;
    private long riceAtAssai;
    private long riceAtCarrefour;
    private long coffeeAtAssai;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        carrefour = data.store("CARREFOUR");
        rice = data.product("ARROZ TIO JOAO TP1", "MERCEARIA");
        riceAtAssai = data.storeProduct(assai, rice, "ARROZ TIO JOAO TP1 5KG");
        riceAtCarrefour = data.storeProduct(carrefour, rice, "ARROZ T.JOAO TP1 5KG");
        coffeeAtAssai = data.storeProduct(assai, data.product("CAFE PILAO", null), "CAFE PILAO 500G");
    }

    @Test
    void summarizesPricesAcrossStoresForTheSameProduct() {
        data.receipt(ana, assai, "2026-07-10T15:00:00Z", riceAtAssai, "1", "27.90");
        data.receipt(ana, carrefour, "2026-08-10T15:00:00Z", riceAtCarrefour, "1", "31.50");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.90");

        ProductSummary summary = service.products(ana, 10).getFirst();

        assertThat(summary.name()).isEqualTo("ARROZ TIO JOAO TP1");
        assertThat(summary.categoryLabel()).isEqualTo("Mercearia");
        assertThat(summary.timesBought()).isEqualTo(3);
        assertThat(summary.lastUnitPrice()).isEqualByComparingTo("25.90");
        assertThat(summary.minUnitPrice()).isEqualByComparingTo("25.90");
        assertThat(summary.maxUnitPrice()).isEqualByComparingTo("31.50");
        assertThat(summary.avgUnitPrice()).isEqualByComparingTo("28.43");
        assertThat(summary.lastBoughtAt()).isEqualTo(Instant.parse("2026-09-10T15:00:00Z"));
    }

    @Test
    void listsMostFrequentlyBoughtProductsFirst() {
        data.receipt(ana, assai, "2026-09-01T15:00:00Z", coffeeAtAssai, "1", "18.00");
        data.receipt(ana, assai, "2026-09-02T15:00:00Z", riceAtAssai, "1", "25.90");
        data.receipt(ana, assai, "2026-09-03T15:00:00Z", riceAtAssai, "1", "25.90");

        assertThat(service.products(ana, 10)).extracting(ProductSummary::name)
                .containsExactly("ARROZ TIO JOAO TP1", "CAFE PILAO");
    }

    @Test
    void returnsPriceHistoryOldestFirstWithStore() {
        data.receipt(ana, carrefour, "2026-08-10T15:00:00Z", riceAtCarrefour, "1", "31.50");
        data.receipt(ana, assai, "2026-07-10T15:00:00Z", riceAtAssai, "1", "27.90");

        List<PricePoint> prices = service.history(ana, rice).orElseThrow().prices();

        assertThat(prices).extracting(PricePoint::storeName).containsExactly("ASSAI", "CARREFOUR");
        assertThat(prices.getLast().unitPrice()).isEqualByComparingTo("31.50");
    }

    @Test
    void historyIncludesHowMuchWasBoughtAndPaid() {
        // Sold by weight: 148 g at R$ 21,90/kg
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "0.148", "21.90");

        PricePoint point = service.history(ana, rice).orElseThrow().prices().getFirst();

        assertThat(point.unitPrice()).isEqualByComparingTo("21.90");
        assertThat(point.quantity()).isEqualByComparingTo("0.148");
        assertThat(point.totalPrice()).isEqualByComparingTo("3.24");
    }

    @Test
    void doesNotRevealOtherUsersPurchases() {
        long bia = data.user("bia@example.com");
        data.receipt(bia, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.90");

        assertThat(service.products(ana, 10)).isEmpty();
        assertThat(service.history(ana, rice)).isEmpty();
    }

    @Test
    void ignoresItemsNotLinkedToAProductYet() {
        long unlinked = data.storeProduct(assai, null, "PRODUTO NOVO");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", unlinked, "1", "9.90");

        assertThat(service.products(ana, 10)).isEmpty();
    }
}
