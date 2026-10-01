package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.insight.BestTimeInsight.Finding;
import com.supermarketagent.insight.BestTimeInsight.Status;
import com.supermarketagent.insight.BestTimeStats.Sample;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BestTimeStatsTest {

    @Test
    void findsAClearPattern() {
        // Early month about 8% below the usual price, the rest around it
        Finding finding = BestTimeStats.decide(List.of(
                sample("DAYS_1_10", 0.91, 0.93, 0.92, 0.90, 0.93, 0.92, 0.91, 0.94, 0.92, 0.92),
                sample("DAYS_11_20", 1.01, 1.03, 1.02, 0.99, 1.04, 1.02, 1.01, 1.03, 1.02, 1.03),
                sample("DAYS_21_31", 1.04, 1.06, 1.05, 1.03, 1.07, 1.05, 1.04, 1.06, 1.05, 1.05)));

        assertThat(finding.status()).isEqualTo(Status.PATTERN);
        assertThat(finding.best().key()).isEqualTo("DAYS_1_10");
        assertThat(finding.best().percentVsStoreAverage()).isEqualByComparingTo("-8.0");
        assertThat(finding.percentCheaper()).isGreaterThan(BigDecimal.valueOf(10));
        assertThat(finding.groups()).hasSize(3);
    }

    @Test
    void smallDifferencesAreNoPattern() {
        // 0.4% cheaper: normal price wobble, not worth waiting for
        Finding finding = BestTimeStats.decide(List.of(
                sample("DAYS_1_10", 0.996, 0.995, 0.997, 0.996, 0.995, 0.997, 0.996, 0.996, 0.995, 0.997),
                sample("DAYS_11_20", 1.002, 1.001, 1.003, 1.002, 1.001, 1.003, 1.002, 1.002, 1.001, 1.003),
                sample("DAYS_21_31", 1.003, 1.002, 1.004, 1.003, 1.002, 1.004, 1.003, 1.003, 1.002, 1.004)));

        assertThat(finding.status()).isEqualTo(Status.NO_PATTERN);
        assertThat(finding.best()).isNull();
    }

    @Test
    void bigButNoisyDifferencesAreNoPattern() {
        // 5% cheaper on average, but prices swing so much that it could be one lucky promotion
        Finding finding = BestTimeStats.decide(List.of(
                sample("DAYS_1_10", 0.60, 1.30, 0.70, 1.20, 0.65, 1.25, 0.80, 1.10),
                sample("DAYS_11_20", 0.70, 1.40, 0.80, 1.30, 0.75, 1.35, 0.90, 1.20)));

        assertThat(finding.status()).isEqualTo(Status.NO_PATTERN);
    }

    @Test
    void needsEnoughPurchasesInAtLeastTwoGroups() {
        Finding finding = BestTimeStats.decide(List.of(
                sample("DAYS_1_10", 0.80, 0.81, 0.79),
                sample("DAYS_11_20", 1.01, 1.03, 1.02, 0.99, 1.04, 1.02, 1.01, 1.03, 1.02, 1.03)));

        assertThat(finding.status()).isEqualTo(Status.NOT_ENOUGH_DATA);
        assertThat(finding.groups()).extracting(BestTimeInsight.Group::samples).containsExactly(3, 10);
    }

    @Test
    void weekdaysNeedAStrongerSignal() {
        // Same gap that passes with three groups is not enough among seven, where one is cheapest by luck
        double[] slightlyCheaper = {0.92, 1.03, 0.95, 0.99, 0.93, 1.02, 0.96, 0.98};
        double[] usual = {0.98, 1.03, 1.01, 0.99, 1.04, 1.02, 0.97, 1.02};
        Sample cheaper = sample("MONDAY", slightlyCheaper);
        List<Sample> others = List.of("TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY").stream()
                .map(day -> sample(day, usual)).toList();
        double t = BestTimeStats.welchT(cheaper, others.stream().reduce(Sample::plus).orElseThrow());

        assertThat(t).isBetween(BestTimeStats.MIN_T_FEW_GROUPS, BestTimeStats.MIN_T_MANY_GROUPS);
        List<Sample> week = new ArrayList<>(others);
        week.addFirst(cheaper);
        assertThat(BestTimeStats.decide(week).status()).isEqualTo(Status.NO_PATTERN);
    }

    private static Sample sample(String key, double... ratios) {
        return new Sample(key, key, ratios.length, Arrays.stream(ratios).sum(),
                Arrays.stream(ratios).map(r -> r * r).sum());
    }
}
