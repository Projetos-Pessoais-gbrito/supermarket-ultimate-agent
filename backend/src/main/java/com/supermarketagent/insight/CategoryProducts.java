package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What the user bought in one category, for the "Por categoria" drill-down.
 *
 * @param category enum name, or {@code none} for items without a category yet
 * @param total    same value as the category bar on the dashboard
 */
public record CategoryProducts(String category, String label, BigDecimal total, List<Product> products) {

    /**
     * @param productId canonical product; null for items not matched to a product yet
     */
    public record Product(Long productId, String name, int timesBought, BigDecimal totalSpent,
                          BigDecimal lastUnitPrice, String lastStoreName, Instant lastBoughtAt) {
    }
}
