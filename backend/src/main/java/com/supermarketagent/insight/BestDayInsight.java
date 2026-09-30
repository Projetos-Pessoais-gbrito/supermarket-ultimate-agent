package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.util.List;

/**
 * When prices tend to be lower for the user, comparing each purchase with the average price of the
 * same product (so buying different things on different days does not skew the result).
 *
 * @param comparableItems purchases of products bought at least twice, the base of the comparison
 * @param bestPeriod      period of the month with the lowest prices, null without enough data
 * @param bestWeekday     day of the week with the lowest prices, null without enough data
 */
public record BestDayInsight(
        int comparableItems,
        Group bestPeriod,
        List<Group> byPeriodOfMonth,
        Group bestWeekday,
        List<Group> byWeekday) {

    /**
     * @param key              stable id, e.g. {@code DAYS_1_10} or {@code MONDAY}
     * @param label            pt-BR label
     * @param percentVsAverage average price difference against each product's own average (-3.5 = 3.5% cheaper)
     * @param samples          purchases in this group
     */
    public record Group(String key, String label, BigDecimal percentVsAverage, int samples) {
    }
}
