package com.supermarketagent.shopping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Products the user buys regularly and is probably running out of.
 *
 * @param items most overdue first
 */
public record ShoppingSuggestions(List<Suggestion> items) {

    /**
     * @param averageIntervalDays usual days between purchases
     * @param daysSinceLastPurchase days since the last purchase (São Paulo dates)
     * @param usualQuantity       average quantity bought per purchase
     * @param bestRecentPrice     lowest unit price within 60 days before the last purchase
     * @param bestRecentStore     where that price was paid
     * @param inList              already on the user's list, not yet checked
     */
    public record Suggestion(
            long productId,
            String name,
            int averageIntervalDays,
            int daysSinceLastPurchase,
            LocalDate lastPurchase,
            BigDecimal usualQuantity,
            BigDecimal lastPrice,
            BigDecimal bestRecentPrice,
            String bestRecentStore,
            boolean inList) {
    }
}
