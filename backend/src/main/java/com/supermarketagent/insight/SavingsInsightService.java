package com.supermarketagent.insight;

import com.supermarketagent.insight.SavingsInsight.ProductSavings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Potential savings and the cheapest store per product, from the user's own purchases. */
@Service
@Transactional(readOnly = true)
public class SavingsInsightService {

    private static final int TOP_PRODUCTS = 10;

    private final JdbcTemplate jdbc;

    SavingsInsightService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SavingsInsight savings(long userId, Instant now, int days) {
        Timestamp from = Timestamp.from(now.minus(days, ChronoUnit.DAYS));
        List<ProductSavings> all = jdbc.query("""
                WITH items AS (
                    SELECT p.id AS product_id, p.normalized_name AS name, s.name AS store_name, r.issued_at,
                           i.quantity, i.unit_price, i.total_price
                    FROM receipts r
                    JOIN stores s ON s.id = r.store_id
                    JOIN receipt_items i ON i.receipt_id = r.id
                    JOIN store_products sp ON sp.id = i.store_product_id
                    JOIN products p ON p.id = sp.product_id
                    WHERE r.user_id = ? AND r.issued_at >= ?
                ),
                best AS (
                    SELECT DISTINCT ON (product_id) product_id, unit_price AS best_price, store_name AS best_store
                    FROM items
                    ORDER BY product_id, unit_price, issued_at DESC
                )
                SELECT i.product_id, i.name, count(*) AS times_bought, sum(i.total_price) AS total_paid,
                       b.best_price, b.best_store,
                       round(sum((i.unit_price - b.best_price) * i.quantity), 2) AS extra_paid
                FROM items i JOIN best b USING (product_id)
                GROUP BY i.product_id, i.name, b.best_price, b.best_store
                HAVING count(*) > 1
                ORDER BY extra_paid DESC, total_paid DESC""",
                (rs, row) -> new ProductSavings(
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getInt("times_bought"),
                        rs.getBigDecimal("total_paid"),
                        rs.getBigDecimal("best_price"),
                        rs.getString("best_store"),
                        rs.getBigDecimal("extra_paid")),
                userId, from);

        BigDecimal potential = all.stream().map(ProductSavings::extraPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal compared = all.stream().map(ProductSavings::totalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ProductSavings> top = all.stream()
                .filter(product -> product.extraPaid().signum() > 0)
                .limit(TOP_PRODUCTS)
                .toList();
        return new SavingsInsight(days, potential, compared, top);
    }
}
