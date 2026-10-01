package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Every purchase behind {@link SavingsInsight#potentialSavings()}, so the app can show how the
 * total adds up: what was paid and the cheaper purchase of the same product it is compared with.
 *
 * @param months               period in months, including the current one
 * @param comparisonWindowDays how close in time two purchases must be to be compared
 * @param potentialSavings     same total as {@link SavingsInsight#potentialSavings()}
 * @param products             products where money was left on the table, largest first
 */
public record SavingsDetails(int months, int comparisonWindowDays, BigDecimal potentialSavings,
                             List<ProductDetails> products) {

    /** @param purchases the purchases that cost more than the reference, newest first */
    public record ProductDetails(long productId, String name, BigDecimal extraPaid, List<Purchase> purchases) {
    }

    /**
     * A purchase that cost more than the cheapest purchase of the same product within the window.
     *
     * @param extraPaid (unitPrice - bestUnitPrice) x quantity, rounded to cents
     * @param bestUnitPrice the reference: lowest unit price within the window (before or after)
     * @param bestStoreName where the reference price was paid (most recent one on ties)
     * @param bestIssuedAt when the reference price was paid
     */
    public record Purchase(long receiptId, Instant issuedAt, String storeName, BigDecimal quantity, String unit,
                           BigDecimal unitPrice, BigDecimal extraPaid,
                           BigDecimal bestUnitPrice, String bestStoreName, Instant bestIssuedAt) {
    }
}
