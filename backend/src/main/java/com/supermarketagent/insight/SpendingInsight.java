package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param previousMonth        the whole previous month
 * @param previousMonthToDate  the previous month from day 1 to {@code comparedUntilDay}, the fair base for a
 *                             month still in progress
 * @param comparedUntilDay     last day of the month included in both sides of the comparison
 * @param changePercent        current month to date versus {@code previousMonthToDate}; null when nothing
 *                             was spent in that part of the previous month
 * @param monthly              one entry per month of the window, oldest first, including months without purchases
 * @param byStore              spending per store in the window, largest first
 * @param byCategory           spending per product category in the window (item totals), largest first
 */
public record SpendingInsight(
        MonthTotal currentMonth,
        MonthTotal previousMonth,
        MonthTotal previousMonthToDate,
        int comparedUntilDay,
        BigDecimal changePercent,
        List<MonthTotal> monthly,
        List<StoreTotal> byStore,
        List<CategoryTotal> byCategory) {

    /** @param month {@code YYYY-MM} */
    public record MonthTotal(String month, BigDecimal total, int receiptCount) {
    }

    public record StoreTotal(long storeId, String storeName, BigDecimal total, int receiptCount) {
    }

    /** @param category enum name, or null for products not categorized yet */
    public record CategoryTotal(String category, String label, BigDecimal total) {
    }
}
