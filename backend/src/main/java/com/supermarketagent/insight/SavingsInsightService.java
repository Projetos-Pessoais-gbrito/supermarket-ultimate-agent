package com.supermarketagent.insight;

import static com.supermarketagent.insight.InsightPeriod.endOf;
import static com.supermarketagent.insight.InsightPeriod.startOf;

import com.supermarketagent.insight.SavingsInsight.ProductSavings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Potential savings and the cheapest store per product, from the user's own purchases. */
@Service
@Transactional(readOnly = true)
public class SavingsInsightService {

    /** Purchases further apart than this are not compared (prices change with inflation). */
    static final int COMPARISON_WINDOW_DAYS = 60;
    private static final int TOP_PRODUCTS = 10;

    private final JdbcTemplate jdbc;
    private final InsightWindow window;

    SavingsInsightService(JdbcTemplate jdbc, InsightWindow window) {
        this.jdbc = jdbc;
        this.window = window;
    }

    /** @param months period including the current month; {@link InsightWindow#ALL} for everything */
    public SavingsInsight savings(long userId, YearMonth currentMonth, int months) {
        int resolvedMonths = window.months(userId, currentMonth, months);
        Timestamp from = Timestamp.from(startOf(currentMonth.minusMonths(resolvedMonths - 1L)));
        Timestamp to = Timestamp.from(endOf(currentMonth));

        List<ProductSavings> all = jdbc.query("""
                WITH purchases AS (
                    SELECT p.id AS product_id, p.normalized_name AS name,
                           COALESCE(s.display_name, s.name) AS store_name, r.issued_at,
                           i.quantity, i.unit_price, i.total_price
                    FROM receipts r
                    JOIN stores s ON s.id = r.store_id
                    JOIN receipt_items i ON i.receipt_id = r.id
                    JOIN store_products sp ON sp.id = i.store_product_id
                    JOIN products p ON p.id = sp.product_id
                    WHERE r.user_id = ?
                ),
                in_period AS (
                    SELECT * FROM purchases WHERE issued_at >= ? AND issued_at < ?
                ),
                -- Each purchase against the best price of the same product within the window, even
                -- if that other purchase falls just outside the period
                compared AS (
                    SELECT ip.*, nearby.best_price AS nearby_best, nearby.purchases AS nearby_purchases
                    FROM in_period ip
                    JOIN LATERAL (
                        SELECT min(o.unit_price) AS best_price, count(*) AS purchases
                        FROM purchases o
                        WHERE o.product_id = ip.product_id
                          AND o.issued_at BETWEEN ip.issued_at - make_interval(days => ?)
                                              AND ip.issued_at + make_interval(days => ?)
                    ) nearby ON true
                ),
                best AS (
                    SELECT DISTINCT ON (product_id) product_id, unit_price AS best_price, store_name AS best_store
                    FROM in_period
                    ORDER BY product_id, unit_price, issued_at DESC
                )
                SELECT c.product_id, c.name, count(*) AS times_bought,
                       sum(c.total_price) FILTER (WHERE c.nearby_purchases > 1) AS compared_paid,
                       b.best_price, b.best_store,
                       round(sum((c.unit_price - c.nearby_best) * c.quantity), 2) AS extra_paid
                FROM compared c JOIN best b USING (product_id)
                GROUP BY c.product_id, c.name, b.best_price, b.best_store
                HAVING count(*) FILTER (WHERE c.nearby_purchases > 1) > 0
                ORDER BY extra_paid DESC, compared_paid DESC""",
                (rs, row) -> new ProductSavings(
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getInt("times_bought"),
                        rs.getBigDecimal("compared_paid"),
                        rs.getBigDecimal("best_price"),
                        rs.getString("best_store"),
                        rs.getBigDecimal("extra_paid")),
                userId, from, to, COMPARISON_WINDOW_DAYS, COMPARISON_WINDOW_DAYS);

        BigDecimal potential = all.stream().map(ProductSavings::extraPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal compared = all.stream().map(ProductSavings::totalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ProductSavings> top = all.stream()
                .filter(product -> product.extraPaid().signum() > 0)
                .limit(TOP_PRODUCTS)
                .toList();
        return new SavingsInsight(resolvedMonths, COMPARISON_WINDOW_DAYS, potential, compared, top);
    }
}
