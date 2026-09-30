package com.supermarketagent.shopping;

import com.supermarketagent.shopping.PurchaseHistory.Purchase;
import com.supermarketagent.shopping.ShoppingSuggestions.Suggestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests what to buy from the user's own rhythm: a product is due when the time since its last
 * purchase reaches its usual interval between purchases.
 */
@Service
@Transactional(readOnly = true)
public class ShoppingSuggestionService {

    /** Suggest a little before the usual interval ends, so the list is ready for the next trip. */
    static final double DUE_RATIO = 0.8;
    /** Much longer than usual means the user probably stopped buying it. */
    static final double STOPPED_RATIO = 4.0;
    static final int MIN_INTERVAL_DAYS = 2;
    static final int PRICE_WINDOW_DAYS = 60;
    private static final int MAX_ITEMS = 30;

    private final PurchaseHistory history;
    private final JdbcTemplate jdbc;

    ShoppingSuggestionService(PurchaseHistory history, JdbcTemplate jdbc) {
        this.history = history;
        this.jdbc = jdbc;
    }

    public ShoppingSuggestions suggestions(long userId, LocalDate today) {
        Set<Long> inList = new HashSet<>(jdbc.queryForList(
                "SELECT product_id FROM shopping_list_items WHERE user_id = ? AND NOT checked AND product_id IS NOT NULL",
                Long.class, userId));

        List<Scored> scored = new ArrayList<>();
        for (List<Purchase> purchases : history.byProduct(userId).values()) {
            suggestion(purchases, today, inList).ifPresent(scored::add);
        }
        return new ShoppingSuggestions(scored.stream()
                .sorted(Comparator.comparingDouble(Scored::overdue).reversed())
                .limit(MAX_ITEMS)
                .map(Scored::suggestion)
                .toList());
    }

    private static Optional<Scored> suggestion(List<Purchase> purchases, LocalDate today, Set<Long> inList) {
        // One purchase trip per day: several lines of the same product on one day count once
        Map<LocalDate, BigDecimal> quantityByDay = new LinkedHashMap<>();
        for (Purchase purchase : purchases) {
            quantityByDay.merge(purchase.day(), purchase.quantity(), BigDecimal::add);
        }
        List<LocalDate> days = new ArrayList<>(quantityByDay.keySet());
        if (days.size() < 2) {
            return Optional.empty();
        }
        LocalDate first = days.getFirst();
        LocalDate last = days.getLast();
        double averageInterval = (double) ChronoUnit.DAYS.between(first, last) / (days.size() - 1);
        if (averageInterval < MIN_INTERVAL_DAYS) {
            return Optional.empty();
        }
        long daysSince = ChronoUnit.DAYS.between(last, today);
        double overdue = daysSince / averageInterval;
        if (overdue < DUE_RATIO || overdue > STOPPED_RATIO) {
            return Optional.empty();
        }

        Purchase latest = purchases.getLast();
        Purchase best = purchases.stream()
                .filter(p -> !p.day().isBefore(last.minusDays(PRICE_WINDOW_DAYS)))
                .min(Comparator.comparing(Purchase::unitPrice).thenComparing(Purchase::issuedAt, Comparator.reverseOrder()))
                .orElse(latest);
        BigDecimal usualQuantity = quantityByDay.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(days.size()), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        // 10 stays "10", not "1E+1"
        usualQuantity = new BigDecimal(usualQuantity.toPlainString());

        Suggestion suggestion = new Suggestion(
                latest.productId(),
                latest.productName(),
                (int) Math.round(averageInterval),
                (int) daysSince,
                last,
                usualQuantity,
                latest.unitPrice(),
                best.unitPrice(),
                best.storeName(),
                inList.contains(latest.productId()));
        return Optional.of(new Scored(suggestion, overdue));
    }

    private record Scored(Suggestion suggestion, double overdue) {
    }
}
