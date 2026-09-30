package com.supermarketagent.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.budget.BudgetSettings.CategoryLimit;
import com.supermarketagent.budget.BudgetStatus.Line;
import com.supermarketagent.budget.BudgetStatus.State;
import com.supermarketagent.insight.InsightTestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BudgetServiceTest {

    private static final LocalDate SEPTEMBER_15 = LocalDate.of(2026, 9, 15);

    @Autowired
    private BudgetService service;

    @Autowired
    private JdbcTemplate jdbc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long meat;
    private long rice;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        meat = data.storeProduct(assai, data.product("PALETA BOVINA", "CARNES_E_PEIXES"), "PALETA BOVINA KG");
        rice = data.storeProduct(assai, data.product("ARROZ", "MERCEARIA"), "ARROZ 5KG");
    }

    @Test
    void savesAndReadsTheLimits() {
        service.replace(ana, settings("800.00", limit("CARNES_E_PEIXES", "200.00")));

        BudgetSettings saved = service.settings(ana);

        assertThat(saved.overall()).isEqualByComparingTo("800.00");
        assertThat(saved.categories()).singleElement().satisfies(limit -> {
            assertThat(limit.category()).isEqualTo("CARNES_E_PEIXES");
            assertThat(limit.label()).isEqualTo("Carnes e peixes");
            assertThat(limit.limit()).isEqualByComparingTo("200.00");
        });
    }

    @Test
    void replacingRemovesLimitsNoLongerSent() {
        service.replace(ana, settings("800.00", limit("CARNES_E_PEIXES", "200.00")));

        service.replace(ana, settings(null, limit("MERCEARIA", "150.00")));

        BudgetSettings saved = service.settings(ana);
        assertThat(saved.overall()).isNull();
        assertThat(saved.categories()).extracting(CategoryLimit::category).containsExactly("MERCEARIA");
    }

    @Test
    void comparesThisMonthsSpendingWithEachLimit() {
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", meat, "1", "170.00");   // 85% of 200
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", rice, "1", "30.00");    // 20% of 150
        data.receipt(ana, assai, "2026-08-20T15:00:00Z", meat, "1", "999.00");   // last month, ignored
        service.replace(ana, settings("500.00", limit("CARNES_E_PEIXES", "200.00"), limit("MERCEARIA", "150.00")));

        BudgetStatus status = service.status(ana, SEPTEMBER_15);

        assertThat(status.month()).isEqualTo("2026-09");
        assertThat(status.daysElapsed()).isEqualTo(15);
        assertThat(status.daysInMonth()).isEqualTo(30);
        assertThat(status.overall().spent()).isEqualByComparingTo("200.00");
        assertThat(status.overall().percentUsed()).isEqualByComparingTo("40.0");
        assertThat(status.overall().state()).isEqualTo(State.OK);
        Line meatLine = status.categories().getFirst();
        assertThat(meatLine.label()).isEqualTo("Carnes e peixes");
        assertThat(meatLine.percentUsed()).isEqualByComparingTo("85.0");
        assertThat(meatLine.state()).isEqualTo(State.WARNING);
        // 170 in 15 days → 340 by the 30th
        assertThat(meatLine.projected()).isEqualByComparingTo("340.00");
        assertThat(meatLine.projectedOver()).isTrue();
        assertThat(status.categories().getLast().state()).isEqualTo(State.OK);
    }

    @Test
    void flagsLimitsAlreadyExceeded() {
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", meat, "1", "210.00");
        service.replace(ana, settings(null, limit("CARNES_E_PEIXES", "200.00")));

        Line line = service.status(ana, SEPTEMBER_15).categories().getFirst();

        assertThat(line.state()).isEqualTo(State.OVER);
        assertThat(line.percentUsed()).isEqualByComparingTo("105.0");
    }

    @Test
    void usesSaoPauloMonthBoundaries() {
        // 1 October 01:30 UTC is still 30 September in São Paulo
        data.receipt(ana, assai, "2026-10-01T01:30:00Z", rice, "1", "50.00");
        service.replace(ana, settings("100.00"));

        assertThat(service.status(ana, LocalDate.of(2026, 9, 30)).overall().spent()).isEqualByComparingTo("50.00");
    }

    @Test
    void onlySeesTheUsersOwnBudgetAndReceipts() {
        long bia = data.user("bia@example.com");
        data.receipt(bia, assai, "2026-09-05T15:00:00Z", meat, "1", "500.00");
        service.replace(bia, settings("100.00"));
        service.replace(ana, settings("300.00"));

        assertThat(service.settings(ana).overall()).isEqualByComparingTo("300.00");
        assertThat(service.status(ana, SEPTEMBER_15).overall().spent()).isEqualByComparingTo("0");
    }

    @Test
    void withoutLimitsThereIsNothingToCompare() {
        BudgetStatus status = service.status(ana, SEPTEMBER_15);

        assertThat(status.overall()).isNull();
        assertThat(status.categories()).isEmpty();
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> service.replace(ana, settings("0")))
                .isInstanceOf(InvalidBudgetException.class);
        assertThatThrownBy(() -> service.replace(ana, settings("10.005")))
                .isInstanceOf(InvalidBudgetException.class);
        assertThatThrownBy(() -> service.replace(ana, settings(null, limit("NOT_A_CATEGORY", "10.00"))))
                .isInstanceOf(InvalidBudgetException.class);
        assertThatThrownBy(() -> service.replace(ana,
                settings(null, limit("MERCEARIA", "10.00"), limit("MERCEARIA", "20.00"))))
                .isInstanceOf(InvalidBudgetException.class);
        assertThat(service.settings(ana).overall()).isNull();
    }

    private static BudgetSettings settings(String overall, CategoryLimit... categories) {
        return new BudgetSettings(overall == null ? null : new BigDecimal(overall), List.of(categories));
    }

    private static CategoryLimit limit(String category, String limit) {
        return new CategoryLimit(category, null, new BigDecimal(limit));
    }
}
