package com.supermarketagent.insight;

import static com.supermarketagent.insight.InsightPeriod.endOf;
import static com.supermarketagent.insight.InsightPeriod.startOf;

import com.supermarketagent.insight.SavingsDetails.ProductDetails;
import com.supermarketagent.insight.SavingsDetails.Purchase;
import com.supermarketagent.insight.SavingsInsight.ProductSavings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /** The user's purchases ({@code purchases}) and those inside the period ({@code in_period}). */
    private static final String PURCHASES = """
            WITH purchases AS (
                SELECT r.id AS receipt_id, p.id AS product_id, COALESCE(p.display_name, p.normalized_name) AS name,
                       COALESCE(s.display_name, s.name) AS store_name, r.issued_at,
                       i.quantity, i.unit, i.unit_price, i.total_price
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
            """;

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

        List<ProductSavings> all = jdbc.query(PURCHASES + """
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

    /**
     * The purchases behind {@link #savings}: each one that cost more than the cheapest purchase of
     * the same product within {@link #COMPARISON_WINDOW_DAYS}, with that cheaper purchase.
     */
    public SavingsDetails details(long userId, YearMonth currentMonth, int months) {
        int resolvedMonths = window.months(userId, currentMonth, months);
        Timestamp from = Timestamp.from(startOf(currentMonth.minusMonths(resolvedMonths - 1L)));
        Timestamp to = Timestamp.from(endOf(currentMonth));

        record Row(long productId, String name, BigDecimal rawExtra, Purchase purchase) {
        }
        List<Row> rows = jdbc.query(PURCHASES + """
                -- Same reference as savings(): the lowest nearby price, most recent one on ties
                compared AS (
                    SELECT ip.*, best.unit_price AS best_price, best.store_name AS best_store,
                           best.issued_at AS best_issued_at
                    FROM in_period ip
                    JOIN LATERAL (
                        SELECT o.unit_price, o.store_name, o.issued_at
                        FROM purchases o
                        WHERE o.product_id = ip.product_id
                          AND o.issued_at BETWEEN ip.issued_at - make_interval(days => ?)
                                              AND ip.issued_at + make_interval(days => ?)
                        ORDER BY o.unit_price, o.issued_at DESC
                        LIMIT 1
                    ) best ON true
                )
                SELECT *, (unit_price - best_price) * quantity AS extra
                FROM compared
                WHERE unit_price > best_price
                ORDER BY issued_at DESC, receipt_id DESC""",
                (rs, row) -> new Row(
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getBigDecimal("extra"),
                        new Purchase(
                                rs.getLong("receipt_id"),
                                rs.getTimestamp("issued_at").toInstant(),
                                rs.getString("store_name"),
                                rs.getBigDecimal("quantity").stripTrailingZeros(),
                                rs.getString("unit"),
                                rs.getBigDecimal("unit_price"),
                                cents(rs.getBigDecimal("extra")),
                                rs.getBigDecimal("best_price"),
                                rs.getString("best_store"),
                                rs.getTimestamp("best_issued_at").toInstant())),
                userId, from, to, COMPARISON_WINDOW_DAYS, COMPARISON_WINDOW_DAYS);

        // Rounded per product like savings(), so the totals match to the cent
        Map<Long, List<Row>> byProduct = rows.stream()
                .collect(Collectors.groupingBy(Row::productId, LinkedHashMap::new, Collectors.toList()));
        List<ProductDetails> products = byProduct.values().stream()
                .map(productRows -> new ProductDetails(
                        productRows.getFirst().productId(),
                        productRows.getFirst().name(),
                        cents(productRows.stream().map(Row::rawExtra).reduce(BigDecimal.ZERO, BigDecimal::add)),
                        productRows.stream().map(Row::purchase).toList()))
                .filter(product -> product.extraPaid().signum() > 0)
                .sorted(Comparator.comparing(ProductDetails::extraPaid).reversed()
                        .thenComparing(ProductDetails::name))
                .toList();
        BigDecimal potential = products.stream().map(ProductDetails::extraPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SavingsDetails(resolvedMonths, COMPARISON_WINDOW_DAYS, potential, products);
    }

    private static BigDecimal cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
