package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.util.List;

/**
 * When prices tend to be lower, per store: each purchase is compared with what the user usually pays
 * for the same product at the same store, so the result reflects timing and not where they shopped.
 * A pattern is only reported when it is unlikely to be chance (see {@link BestTimeStats}).
 *
 * @param days   history analysed, in days
 * @param stores stores with purchases to compare, the one with the biggest pattern first
 */
public record BestTimeInsight(int days, List<StoreBestTime> stores) {

    public enum Status {
        /** One group is clearly cheaper than the rest */
        PATTERN,
        /** Enough purchases, and prices are about the same whatever the time */
        NO_PATTERN,
        /** Too few purchases to tell */
        NOT_ENOUGH_DATA
    }

    /**
     * @param comparablePurchases purchases of products bought at least twice at this store
     * @param periodOfMonth       days 1-10, 11-20 or 21-31
     * @param weekday             Monday to Sunday
     */
    public record StoreBestTime(long storeId, String storeName, int comparablePurchases,
                                Finding periodOfMonth, Finding weekday) {
    }

    /**
     * @param best          the cheaper group, only with {@link Status#PATTERN}
     * @param percentCheaper how much cheaper {@code best} is than the other groups of the same store
     * @param groups        every group with purchases, in calendar order
     */
    public record Finding(Status status, Group best, BigDecimal percentCheaper, List<Group> groups) {
    }

    /**
     * @param key                   stable id, e.g. {@code DAYS_1_10} or {@code MONDAY}
     * @param label                 pt-BR label
     * @param percentVsStoreAverage average difference against the usual price at the store (-3.5 = 3.5% cheaper)
     * @param samples               purchases in this group
     */
    public record Group(String key, String label, BigDecimal percentVsStoreAverage, int samples) {
    }
}
