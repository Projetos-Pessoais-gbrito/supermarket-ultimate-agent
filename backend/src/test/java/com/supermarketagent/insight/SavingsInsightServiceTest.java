package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.SavingsInsight.ProductSavings;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SavingsInsightServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private SavingsInsightService service;

    @Autowired
    private JdbcTemplate jdbc;

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
}
