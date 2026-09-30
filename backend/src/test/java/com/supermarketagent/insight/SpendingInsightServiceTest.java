package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.SpendingInsight.MonthTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class SpendingInsightServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private SpendingInsightService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long carrefour;
    private long rice;
    private long soap;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        carrefour = data.store("CARREFOUR");
        rice = data.storeProduct(assai, data.product("ARROZ TIO JOAO", "MERCEARIA"), "ARROZ TIO JOAO 5KG");
        soap = data.storeProduct(carrefour, null, "SABAO EM PO");
    }

    @Test
    void comparesTheCurrentMonthWithThePreviousOne() {
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", rice, "4", "25.00");   // 100
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", rice, "6", "25.00");   // 150

        SpendingInsight insight = service.spending(ana, SEPTEMBER, 6);

        assertThat(insight.currentMonth()).isEqualTo(month("2026-09", "150.00", 1));
        assertThat(insight.previousMonth()).isEqualTo(month("2026-08", "100.00", 1));
        assertThat(insight.changePercent()).isEqualByComparingTo("50.0");
    }

    @Test
    void comparesAMonthInProgressWithTheSameDaysOfThePreviousMonth() {
        data.receipt(ana, assai, "2026-08-05T15:00:00Z", rice, "4", "25.00");   // 100, before day 10
        data.receipt(ana, assai, "2026-08-25T15:00:00Z", rice, "8", "25.00");   // 200, after day 10
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", rice, "6", "25.00");   // 150

        SpendingInsight insight = service.spending(ana, LocalDate.of(2026, 9, 10), 6);

        assertThat(insight.comparedUntilDay()).isEqualTo(10);
        assertThat(insight.previousMonthToDate()).isEqualTo(month("2026-08", "100.00", 1));
        assertThat(insight.previousMonth().total()).isEqualByComparingTo("300.00");
        assertThat(insight.changePercent()).isEqualByComparingTo("50.0");
    }

    @Test
    void usesTheWholePreviousMonthWhenItIsShorter() {
        // 31 March against February (28 days): all of February
        data.receipt(ana, assai, "2026-02-27T15:00:00Z", rice, "4", "25.00");
        data.receipt(ana, assai, "2026-03-05T15:00:00Z", rice, "2", "25.00");

        SpendingInsight insight = service.spending(ana, LocalDate.of(2026, 3, 31), 3);

        assertThat(insight.comparedUntilDay()).isEqualTo(28);
        assertThat(insight.previousMonthToDate().total()).isEqualByComparingTo("100.00");
        assertThat(insight.changePercent()).isEqualByComparingTo("-50.0");
    }

    @Test
    void countsPurchasesLateOnTheLastComparedDayInSaoPauloTime() {
        // 11 August 02:00 UTC is still 10 August 23:00 in São Paulo
        data.receipt(ana, assai, "2026-08-11T02:00:00Z", rice, "1", "25.00");

        assertThat(service.spending(ana, LocalDate.of(2026, 9, 10), 3).previousMonthToDate().total())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void listsEveryMonthOfTheWindowIncludingEmptyOnes() {
        data.receipt(ana, assai, "2026-06-10T15:00:00Z", rice, "1", "25.00");

        SpendingInsight insight = service.spending(ana, SEPTEMBER, 6);

        assertThat(insight.monthly()).extracting(MonthTotal::month)
                .containsExactly("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09");
        assertThat(insight.monthly().get(2).total()).isEqualByComparingTo("25.00");
        assertThat(insight.monthly().get(3).total()).isEqualByComparingTo("0");
        assertThat(insight.changePercent()).as("nothing spent last month").isNull();
    }

    @Test
    void usesSaoPauloTimeForMonthBoundaries() {
        // 1 October 01:30 UTC is still 30 September 22:30 in São Paulo
        data.receipt(ana, assai, "2026-10-01T01:30:00Z", rice, "1", "25.00");

        assertThat(service.spending(ana, SEPTEMBER, 1).currentMonth().total()).isEqualByComparingTo("25.00");
    }

    @Test
    void splitsSpendingByStoreAndCategory() {
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", rice, "2", "25.00");     // 50 MERCEARIA
        data.receipt(ana, carrefour, "2026-09-06T15:00:00Z", soap, "1", "18.90"); // uncategorized

        SpendingInsight insight = service.spending(ana, SEPTEMBER, 3);

        assertThat(insight.byStore()).extracting(SpendingInsight.StoreTotal::storeName)
                .containsExactly("ASSAI", "CARREFOUR");
        assertThat(insight.byCategory()).extracting(SpendingInsight.CategoryTotal::label)
                .containsExactly("Mercearia", "Sem categoria");
        assertThat(insight.byCategory().getFirst().total()).isEqualByComparingTo("50.00");
    }

    @Test
    void combinesBranchesOfTheSameChain() {
        long branch1 = data.store("SENDAS DISTRIBUIDORA S/A", "ASSAI");
        long branch2 = data.store("SENDAS DISTRIBUIDORA S/A", "ASSAI");
        long riceAt1 = data.storeProduct(branch1, null, "ARROZ");
        long riceAt2 = data.storeProduct(branch2, null, "ARROZ");
        data.receipt(ana, branch1, "2026-09-05T15:00:00Z", riceAt1, "1", "25.00");
        data.receipt(ana, branch2, "2026-09-06T15:00:00Z", riceAt2, "1", "26.00");

        var byStore = service.spending(ana, SEPTEMBER, 1).byStore();

        assertThat(byStore).hasSize(1);
        assertThat(byStore.getFirst().storeName()).isEqualTo("ASSAI");
        assertThat(byStore.getFirst().total()).isEqualByComparingTo("51.00");
        assertThat(byStore.getFirst().receiptCount()).isEqualTo(2);
    }

    @Test
    void ignoresOtherUsersReceipts() {
        long bia = data.user("bia@example.com");
        data.receipt(bia, assai, "2026-09-10T15:00:00Z", rice, "10", "25.00");

        SpendingInsight insight = service.spending(ana, SEPTEMBER, 6);

        assertThat(insight.currentMonth().total()).isEqualByComparingTo("0");
        assertThat(insight.byStore()).isEmpty();
    }

    @Test
    void allCoversEveryMonthSinceTheFirstReceipt() {
        data.receipt(ana, assai, "2026-03-13T15:00:00Z", rice, "1", "25.00");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", rice, "1", "25.00");

        SpendingInsight insight = service.spending(ana, SEPTEMBER, InsightWindow.ALL);

        assertThat(insight.monthly()).extracting(MonthTotal::month).first().isEqualTo("2026-03");
        assertThat(insight.monthly()).hasSize(7);
        assertThat(insight.byStore().getFirst().total()).isEqualByComparingTo("50.00");
    }

    @Test
    void allWithoutReceiptsShowsJustTheCurrentMonth() {
        assertThat(service.spending(ana, SEPTEMBER, InsightWindow.ALL).monthly()).hasSize(1);
    }

    @Test
    void endpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/insights/spending")).andExpect(status().isUnauthorized());
    }

    private static MonthTotal month(String month, String total, int receipts) {
        return new MonthTotal(month, new BigDecimal(total), receipts);
    }
}
