package com.supermarketagent.receipt.query;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Filtered receipt listing: one page plus per-month totals of everything that matched. */
public final class ReceiptSearch {

    private ReceiptSearch() {
    }

    /**
     * Optional filters, all combined with AND.
     *
     * @param store the store name as shown in the app (display name, else legal name)
     * @param month the São Paulo calendar month the receipt was issued in
     * @param text  matched, ignoring case and accents, against item descriptions and product names
     */
    public record Filter(String store, YearMonth month, String text) {

        public static final Filter NONE = new Filter(null, null, null);
    }

    /** {@code monthlyTotals} cover every matching receipt, not only the current page. */
    public record Result(List<ReceiptSummary> content, int page, int size, long totalElements, int totalPages,
                         List<MonthlyTotal> monthlyTotals, List<String> stores, List<String> months) {
    }

    public record MonthlyTotal(String month, BigDecimal total, long receiptCount) {
    }
}
