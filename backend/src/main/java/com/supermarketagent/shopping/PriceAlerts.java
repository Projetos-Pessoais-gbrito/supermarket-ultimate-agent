package com.supermarketagent.shopping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Products whose latest price is notably different from what the user usually pays. */
public record PriceAlerts(List<Alert> items) {

    public enum Type {
        /** Latest price clearly below the usual price. */
        DEAL,
        /** Latest price clearly above the usual price. */
        RISE
    }

    /**
     * @param usualPrice    average unit price of the earlier purchases in the window
     * @param changePercent latest vs usual, e.g. -15.0 for 15% cheaper
     */
    public record Alert(long productId, String name, Type type, BigDecimal latestPrice, BigDecimal usualPrice,
                        BigDecimal changePercent, String store, LocalDate date) {
    }
}
