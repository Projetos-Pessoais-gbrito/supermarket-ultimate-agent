package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.util.List;

/**
 * Personal inflation: how the prices of the user's own basket changed month over month.
 *
 * @param monthly  one entry per month of the window, oldest first
 * @param changes  products of the current month's basket, biggest price increases first
 */
public record InflationInsight(List<MonthChange> monthly, List<ProductChange> changes) {

    /**
     * @param month            {@code YYYY-MM}
     * @param changePercent    weighted price change against the previous month; null without comparable products
     * @param productsCompared products bought in both months
     */
    public record MonthChange(String month, BigDecimal changePercent, int productsCompared) {
    }

    public record ProductChange(long productId, String name, BigDecimal previousPrice, BigDecimal currentPrice,
                                BigDecimal changePercent) {
    }
}
