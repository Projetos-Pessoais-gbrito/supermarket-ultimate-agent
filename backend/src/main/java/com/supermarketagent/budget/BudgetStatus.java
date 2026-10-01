package com.supermarketagent.budget;

import java.math.BigDecimal;
import java.util.List;

/**
 * This month's spending against the user's limits (São Paulo months).
 *
 * @param month       {@code YYYY-MM}
 * @param daysElapsed days of the month so far, including today
 * @param overall     overall limit status; null when no overall limit is set
 * @param categories  per-category limit statuses, most used first
 */
public record BudgetStatus(String month, int daysElapsed, int daysInMonth, Line overall, List<Line> categories) {

    public enum State {
        OK,
        /** 80% or more of the limit already spent */
        WARNING,
        /** more than the limit spent */
        OVER
    }

    /**
     * @param category      enum name; null for the overall limit
     * @param percentUsed   spent / limit x 100, one decimal
     * @param projected     spending at the current pace by the end of the month
     * @param projectedOver whether the projection passes the limit
     */
    public record Line(String category, String label, BigDecimal limit, BigDecimal spent, BigDecimal percentUsed,
                       BigDecimal projected, boolean projectedOver, State state) {
    }
}
