package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.BestDayInsight.Group;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BestDayInsightServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Autowired
    private BestDayInsightService service;

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
    void findsThePeriodOfTheMonthWithLowerPrices() {
        // Early month is consistently cheaper for both products, over three months
        for (String month : new String[] {"06", "07", "08"}) {
            data.receipt(ana, assai, "2026-" + month + "-05T15:00:00Z", rice, "1", "24.00");
            data.receipt(ana, assai, "2026-" + month + "-25T15:00:00Z", rice, "1", "28.00");
            data.receipt(ana, assai, "2026-" + month + "-06T15:00:00Z", coffee, "1", "15.00");
            data.receipt(ana, assai, "2026-" + month + "-26T15:00:00Z", coffee, "1", "17.00");
        }

        BestDayInsight insight = service.bestDay(ana, NOW, 365);

        assertThat(insight.comparableItems()).isEqualTo(12);
        assertThat(insight.bestPeriod().key()).isEqualTo("DAYS_1_10");
        assertThat(insight.bestPeriod().label()).isEqualTo("Dias 1 a 10");
        assertThat(insight.bestPeriod().percentVsAverage()).isNegative();
        assertThat(insight.byPeriodOfMonth()).extracting(Group::key).containsExactly("DAYS_1_10", "DAYS_21_31");
    }

    @Test
    void comparesEachProductWithItsOwnAverage() {
        // Expensive coffee always on the 5th, cheap rice always on the 25th: without the
        // per-product comparison, day 5 would wrongly look expensive.
        for (String month : new String[] {"06", "07", "08"}) {
            data.receipt(ana, assai, "2026-" + month + "-05T15:00:00Z", coffee, "1", "50.00");
            data.receipt(ana, assai, "2026-" + month + "-25T15:00:00Z", rice, "1", "5.00");
        }

        BestDayInsight insight = service.bestDay(ana, NOW, 365);

        assertThat(insight.byPeriodOfMonth()).allSatisfy(group ->
                assertThat(group.percentVsAverage()).isEqualByComparingTo("0"));
    }

    @Test
    void usesSaoPauloDayForPurchasesLateAtNight() {
        // 11 June 01:00 UTC is 10 June 22:00 in São Paulo: still in days 1 to 10
        data.receipt(ana, assai, "2026-06-11T01:00:00Z", rice, "1", "24.00");
        data.receipt(ana, assai, "2026-07-11T01:00:00Z", rice, "1", "24.00");

        assertThat(service.bestDay(ana, NOW, 365).byPeriodOfMonth()).extracting(Group::key)
                .containsExactly("DAYS_1_10");
    }

    @Test
    void doesNotRecommendWithoutEnoughData() {
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", rice, "1", "24.00");
        data.receipt(ana, assai, "2026-09-25T15:00:00Z", rice, "1", "28.00");

        BestDayInsight insight = service.bestDay(ana, NOW, 365);

        assertThat(insight.bestPeriod()).isNull();
        assertThat(insight.bestWeekday()).isNull();
        assertThat(insight.comparableItems()).isEqualTo(2);
    }

    @Test
    void ignoresProductsBoughtOnlyOnce() {
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", rice, "1", "24.00");

        assertThat(service.bestDay(ana, NOW, 365).comparableItems()).isZero();
    }
}
