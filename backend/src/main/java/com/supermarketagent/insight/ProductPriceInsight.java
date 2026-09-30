package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Price data for products the user bought, per canonical product (same package size everywhere). */
public final class ProductPriceInsight {

    private ProductPriceInsight() {
    }

    /**
     * @param categoryLabel pt-BR category, null while not categorized
     * @param lastUnitPrice price paid the last time it was bought
     */
    public record ProductSummary(
            long productId,
            String name,
            String categoryLabel,
            int timesBought,
            BigDecimal lastUnitPrice,
            BigDecimal minUnitPrice,
            BigDecimal maxUnitPrice,
            BigDecimal avgUnitPrice,
            Instant lastBoughtAt) {
    }

    public record PricePoint(Instant issuedAt, long storeId, String storeName, BigDecimal unitPrice, String unit) {
    }

    public record PriceHistory(long productId, String name, List<PricePoint> prices) {
    }
}
