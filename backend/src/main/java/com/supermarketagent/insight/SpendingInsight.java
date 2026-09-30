package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param changePercent current month versus previous month; null when nothing was spent last month
 * @param monthly       one entry per month of the window, oldest first, including months without purchases
 * @param byStore       spending per store in the window, largest first
 * @param byCategory    spending per product category in the window (item totals), largest first
 */
public record SpendingInsight(
        MonthTotal currentMonth,
        MonthTotal previousMonth,
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
