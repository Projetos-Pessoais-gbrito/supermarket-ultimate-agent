package com.supermarketagent.insight;

import com.supermarketagent.catalog.ProductCategory;
import com.supermarketagent.insight.ProductPriceInsight.PriceHistory;
import com.supermarketagent.insight.ProductPriceInsight.PricePoint;
import com.supermarketagent.insight.ProductPriceInsight.ProductSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Price history of the products a user buys. Only items already linked to a canonical product count. */
@Service
@Transactional(readOnly = true)
public class ProductPriceInsightService {

    private final JdbcTemplate jdbc;

    ProductPriceInsightService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Most frequently bought products first. */
    public List<ProductSummary> products(long userId, int limit) {
        return jdbc.query("""
                WITH bought AS (
                    SELECT p.id, p.normalized_name, p.category, i.unit_price, r.issued_at,
                           row_number() OVER (PARTITION BY p.id ORDER BY r.issued_at DESC, i.id DESC) AS recency
                    FROM receipts r
                    JOIN receipt_items i ON i.receipt_id = r.id
                    JOIN store_products sp ON sp.id = i.store_product_id
                    JOIN products p ON p.id = sp.product_id
                    WHERE r.user_id = ?
                )
                SELECT id, normalized_name, category, count(*) AS times_bought,
                       max(unit_price) FILTER (WHERE recency = 1) AS last_price,
                       min(unit_price) AS min_price, max(unit_price) AS max_price,
                       round(avg(unit_price), 2) AS avg_price, max(issued_at) AS last_bought
                FROM bought
                GROUP BY id, normalized_name, category
                ORDER BY times_bought DESC, last_bought DESC
                LIMIT ?""",
                (rs, row) -> new ProductSummary(
                        rs.getLong("id"),
                        rs.getString("normalized_name"),
                        label(rs.getString("category")),
                        rs.getInt("times_bought"),
                        rs.getBigDecimal("last_price"),
                        rs.getBigDecimal("min_price"),
                        rs.getBigDecimal("max_price"),
                        rs.getBigDecimal("avg_price"),
                        rs.getTimestamp("last_bought").toInstant()),
                userId, limit);
    }

    /** Every price the user paid for the product, oldest first; empty if they never bought it. */
    public Optional<PriceHistory> history(long userId, long productId) {
        List<PricePoint> prices = jdbc.query("""
                SELECT r.issued_at, s.id AS store_id, s.name AS store_name, i.unit_price, i.unit
                FROM receipts r
                JOIN stores s ON s.id = r.store_id
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                WHERE r.user_id = ? AND sp.product_id = ?
                ORDER BY r.issued_at, i.id""",
                (rs, row) -> new PricePoint(rs.getTimestamp("issued_at").toInstant(), rs.getLong("store_id"),
                        rs.getString("store_name"), rs.getBigDecimal("unit_price"), rs.getString("unit")),
                userId, productId);
        if (prices.isEmpty()) {
            return Optional.empty();
        }
        String name = jdbc.queryForObject("SELECT normalized_name FROM products WHERE id = ?", String.class, productId);
        return Optional.of(new PriceHistory(productId, name, prices));
    }

    private static String label(String category) {
        return category == null ? null : ProductCategory.valueOf(category).label();
    }
}
