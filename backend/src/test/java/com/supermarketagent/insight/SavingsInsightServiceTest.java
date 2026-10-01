package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.SavingsDetails.ProductDetails;
import com.supermarketagent.insight.SavingsDetails.Purchase;
import com.supermarketagent.insight.SavingsInsight.ProductSavings;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SavingsInsightServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private SavingsInsightService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long carrefour;
    private long riceAtAssai;
    private long riceAtCarrefour;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        carrefour = data.store("CARREFOUR");
        long rice = data.product("ARROZ TIO JOAO TP1", "MERCEARIA");
        riceAtAssai = data.storeProduct(assai, rice, "ARROZ TIO JOAO TP1 5KG");
        riceAtCarrefour = data.storeProduct(carrefour, rice, "ARROZ T.JOAO TP1 5KG");
    }

    @Test
    void addsUpWhatWasPaidAboveTheBestNearbyPrice() {
        data.receipt(ana, assai, "2026-08-01T15:00:00Z", riceAtAssai, "2", "25.90");       // best
        data.receipt(ana, carrefour, "2026-09-01T15:00:00Z", riceAtCarrefour, "2", "31.40"); // 2 x 5.50 = 11.00
        data.receipt(ana, carrefour, "2026-09-20T15:00:00Z", riceAtCarrefour, "1", "28.90"); // 1 x 3.00 = 3.00

        SavingsInsight insight = service.savings(ana, SEPTEMBER, 3);

        assertThat(insight.potentialSavings()).isEqualByComparingTo("14.00");
        assertThat(insight.comparisonWindowDays()).isEqualTo(60);
        ProductSavings rice = insight.products().getFirst();
        assertThat(rice.bestUnitPrice()).isEqualByComparingTo("25.90");
        assertThat(rice.bestStoreName()).isEqualTo("ASSAI");
        assertThat(rice.timesBought()).isEqualTo(3);
        assertThat(rice.extraPaid()).isEqualByComparingTo("14.00");
    }

    @Test
    void neverComparesPricesMonthsApart() {
        // A January price is not a fair reference for September (inflation)
        data.receipt(ana, assai, "2026-01-10T15:00:00Z", riceAtAssai, "1", "19.90");
        data.receipt(ana, carrefour, "2026-09-01T15:00:00Z", riceAtCarrefour, "1", "31.40");
        data.receipt(ana, carrefour, "2026-09-20T15:00:00Z", riceAtCarrefour, "1", "28.90");

        SavingsInsight insight = service.savings(ana, SEPTEMBER, InsightWindow.ALL);

        assertThat(insight.potentialSavings()).isEqualByComparingTo("2.50");
    }

    @Test
    void allIncludesOldReceipts() {
        data.receipt(ana, assai, "2026-03-05T15:00:00Z", riceAtAssai, "1", "22.00");
        data.receipt(ana, carrefour, "2026-03-20T15:00:00Z", riceAtCarrefour, "1", "26.00");

        assertThat(service.savings(ana, SEPTEMBER, 3).potentialSavings()).isEqualByComparingTo("0");
        SavingsInsight all = service.savings(ana, SEPTEMBER, InsightWindow.ALL);
        assertThat(all.potentialSavings()).isEqualByComparingTo("4.00");
        assertThat(all.months()).isEqualTo(7);
    }

    @Test
    void comparesWithPurchasesJustOutsideThePeriod() {
        data.receipt(ana, assai, "2026-08-25T15:00:00Z", riceAtAssai, "1", "25.00");        // before the period
        data.receipt(ana, carrefour, "2026-09-05T15:00:00Z", riceAtCarrefour, "1", "29.00"); // in September

        assertThat(service.savings(ana, SEPTEMBER, 1).potentialSavings()).isEqualByComparingTo("4.00");
    }

    @Test
    void needsSomethingToCompareWith() {
        data.receipt(ana, carrefour, "2026-09-01T15:00:00Z", riceAtCarrefour, "1", "31.40");

        SavingsInsight insight = service.savings(ana, SEPTEMBER, 3);

        assertThat(insight.potentialSavings()).isEqualByComparingTo("0");
        assertThat(insight.products()).isEmpty();
    }

    @Test
    void ignoresOtherUsersPrices() {
        long bia = data.user("bia@example.com");
        data.receipt(bia, assai, "2026-09-01T15:00:00Z", riceAtAssai, "1", "19.90");
        data.receipt(ana, carrefour, "2026-09-01T15:00:00Z", riceAtCarrefour, "1", "31.40");
        data.receipt(ana, carrefour, "2026-09-20T15:00:00Z", riceAtCarrefour, "1", "31.40");

        assertThat(service.savings(ana, SEPTEMBER, 3).potentialSavings()).isEqualByComparingTo("0");
    }

    @Test
    void detailsShowEachPurchaseAndWhatItIsComparedWith() {
        data.receipt(ana, assai, "2026-08-01T15:00:00Z", riceAtAssai, "2", "25.90");       // best
        data.receipt(ana, carrefour, "2026-09-01T15:00:00Z", riceAtCarrefour, "2", "31.40"); // 2 x 5.50 = 11.00
        data.receipt(ana, carrefour, "2026-09-20T15:00:00Z", riceAtCarrefour, "1", "28.90"); // 1 x 3.00 = 3.00

        SavingsDetails details = service.details(ana, SEPTEMBER, 3);

        assertThat(details.potentialSavings()).isEqualByComparingTo("14.00");
        ProductDetails rice = details.products().getFirst();
        assertThat(rice.extraPaid()).isEqualByComparingTo("14.00");
        // The cheapest purchase itself is not listed; newest first
        assertThat(rice.purchases()).hasSize(2);
        Purchase latest = rice.purchases().getFirst();
        assertThat(latest.issuedAt()).isEqualTo(Instant.parse("2026-09-20T15:00:00Z"));
        assertThat(latest.storeName()).isEqualTo("CARREFOUR");
        assertThat(latest.quantity()).isEqualByComparingTo("1");
        assertThat(latest.unitPrice()).isEqualByComparingTo("28.90");
        assertThat(latest.extraPaid()).isEqualByComparingTo("3.00");
        assertThat(latest.bestUnitPrice()).isEqualByComparingTo("25.90");
        assertThat(latest.bestStoreName()).isEqualTo("ASSAI");
        assertThat(latest.bestIssuedAt()).isEqualTo(Instant.parse("2026-08-01T15:00:00Z"));
        assertThat(rice.purchases().get(1).extraPaid()).isEqualByComparingTo("11.00");
    }

    @Test
    void detailsCompareEachPurchaseWithItsOwnNearbyBestPrice() {
        data.receipt(ana, assai, "2026-01-10T15:00:00Z", riceAtAssai, "1", "19.90");
        data.receipt(ana, carrefour, "2026-02-20T15:00:00Z", riceAtCarrefour, "1", "24.00"); // vs January: 4.10
        data.receipt(ana, carrefour, "2026-04-15T15:00:00Z", riceAtCarrefour, "1", "26.00"); // vs February: 2.00

        ProductDetails rice = service.details(ana, SEPTEMBER, InsightWindow.ALL).products().getFirst();

        assertThat(rice.purchases()).extracting(Purchase::bestUnitPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("24.00"), new BigDecimal("19.90"));
        assertThat(rice.extraPaid()).isEqualByComparingTo("6.10");
    }

    @Test
    void detailsAddUpToTheSavingsTotal() {
        long beans = data.product("FEIJAO CARIOCA", "MERCEARIA");
        long beansAtAssai = data.storeProduct(assai, beans, "FEIJAO CARIOCA KG");
        long beansAtCarrefour = data.storeProduct(carrefour, beans, "FEIJAO CARIOCA 1KG");
        data.receipt(ana, assai, "2026-08-01T15:00:00Z", riceAtAssai, "2", "25.90");
        data.receipt(ana, carrefour, "2026-09-01T15:00:00Z", riceAtCarrefour, "2", "31.40");
        data.receipt(ana, assai, "2026-09-02T15:00:00Z", beansAtAssai, "0.333", "7.99");
        data.receipt(ana, carrefour, "2026-09-03T15:00:00Z", beansAtCarrefour, "0.777", "9.49");
        data.receipt(ana, carrefour, "2026-09-10T15:00:00Z", beansAtCarrefour, "1.115", "8.79");

        SavingsInsight savings = service.savings(ana, SEPTEMBER, 3);
        SavingsDetails details = service.details(ana, SEPTEMBER, 3);

        assertThat(details.potentialSavings()).isEqualByComparingTo(savings.potentialSavings());
        assertThat(details.products()).extracting(ProductDetails::name)
                .containsExactlyElementsOf(savings.products().stream().map(ProductSavings::name).toList());
    }

    @Test
    void detailsIgnoreOtherUsers() {
        long bia = data.user("bia@example.com");
        data.receipt(bia, assai, "2026-09-01T15:00:00Z", riceAtAssai, "1", "19.90");
        data.receipt(ana, carrefour, "2026-09-01T15:00:00Z", riceAtCarrefour, "1", "31.40");

        assertThat(service.details(ana, SEPTEMBER, 3).products()).isEmpty();
    }

    @Test
    void detailsEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/insights/savings/details")).andExpect(status().isUnauthorized());
    }
}
