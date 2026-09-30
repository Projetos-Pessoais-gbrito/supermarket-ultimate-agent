package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.InflationInsight.MonthChange;
import com.supermarketagent.insight.InflationInsight.ProductChange;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InflationInsightServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private InflationInsightService service;

    @Autowired
    private JdbcTemplate jdbc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long rice;
    private long coffee;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        rice = data.storeProduct(assai, data.product("ARROZ", "MERCEARIA"), "ARROZ 5KG");
        coffee = data.storeProduct(assai, data.product("CAFE", "MERCEARIA"), "CAFE 500G");
    }

    @Test
    void weighsEachProductByWhatWasSpentOnItLastMonth() {
        // August: 75 on rice, 25 on coffee. September: rice +10%, coffee -20%.
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", rice, "3", "25.00");
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", coffee, "1", "25.00");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", rice, "1", "27.50");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", coffee, "1", "20.00");

        MonthChange september = service.inflation(ana, SEPTEMBER, 6).monthly().getLast();

        // 0.75 x 1.10 + 0.25 x 0.80 = 1.025
        assertThat(september.changePercent()).isEqualByComparingTo("2.5");
        assertThat(september.productsCompared()).isEqualTo(2);
    }

    @Test
    void listsProductsWithTheBiggestIncreaseFirst() {
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", rice, "1", "25.00");
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", coffee, "1", "25.00");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", rice, "1", "27.50");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", coffee, "1", "20.00");

        var changes = service.inflation(ana, SEPTEMBER, 6).changes();

        assertThat(changes).extracting(ProductChange::name).containsExactly("ARROZ", "CAFE");
        assertThat(changes.getFirst().changePercent()).isEqualByComparingTo("10.0");
        assertThat(changes.getLast().changePercent()).isEqualByComparingTo("-20.0");
    }

    @Test
    void comparesTheFirstMonthOfTheWindowWithTheMonthBefore() {
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", rice, "1", "25.00");
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", rice, "1", "26.00");

        assertThat(service.inflation(ana, SEPTEMBER, 1).monthly())
                .containsExactly(new MonthChange("2026-09", new BigDecimal("4.0"), 1));
    }

    @Test
    void leavesMonthsWithoutComparableProductsEmpty() {
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", rice, "1", "26.00");

        var monthly = service.inflation(ana, SEPTEMBER, 3).monthly();

        assertThat(monthly).extracting(MonthChange::month).containsExactly("2026-07", "2026-08", "2026-09");
        assertThat(monthly).allSatisfy(month -> assertThat(month.changePercent()).isNull());
    }
}
