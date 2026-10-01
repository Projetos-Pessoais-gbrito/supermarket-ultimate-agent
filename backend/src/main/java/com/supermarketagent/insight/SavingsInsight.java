package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.util.List;

/**
 * How much the user could have saved: each purchase is compared with the lowest price the user
 * paid for the same product within {@code comparisonWindowDays} of it (before or after), so old
 * receipts count and prices from different years are never compared.
 *
 * @param months               period in months, including the current one
 * @param comparisonWindowDays how close in time two purchases must be to be compared
 * @param potentialSavings     sum over purchases of (price paid - nearby best price) x quantity
 * @param comparedSpending     total paid for purchases that had something to compare with
 * @param products             products with the most money left on the table, largest first
 */
public record SavingsInsight(int months, int comparisonWindowDays, BigDecimal potentialSavings,
                             BigDecimal comparedSpending, List<ProductSavings> products) {

    /**
     * @param bestUnitPrice lowest unit price paid in the period
     * @param bestStoreName where that price was paid (most recent one on ties)
     * @param extraPaid     money that would have been saved paying the nearby best price every time
     */
    public record ProductSavings(long productId, String name, int timesBought, BigDecimal totalPaid,
                                 BigDecimal bestUnitPrice, String bestStoreName, BigDecimal extraPaid) {
    }
}
