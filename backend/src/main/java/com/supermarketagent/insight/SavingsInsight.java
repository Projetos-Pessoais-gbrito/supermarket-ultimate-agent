package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.util.List;

/**
 * How much the user could have saved by always paying the lowest price they themselves paid
 * for each product in the period.
 *
 * @param days             period length
 * @param potentialSavings sum over purchases of (price paid - best price) x quantity
 * @param comparedSpending total paid for products bought more than once, the base of the comparison
 * @param products         products with the most money left on the table, largest first
 */
public record SavingsInsight(int days, BigDecimal potentialSavings, BigDecimal comparedSpending,
                             List<ProductSavings> products) {

    /**
     * @param bestUnitPrice lowest unit price paid in the period
     * @param bestStoreName where that price was paid (most recent one on ties)
     * @param extraPaid     money that would have been saved paying the best price every time
     */
    public record ProductSavings(long productId, String name, int timesBought, BigDecimal totalPaid,
                                 BigDecimal bestUnitPrice, String bestStoreName, BigDecimal extraPaid) {
    }
}
