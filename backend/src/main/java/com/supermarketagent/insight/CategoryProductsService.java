package com.supermarketagent.insight;

import static com.supermarketagent.insight.InsightPeriod.endOf;
import static com.supermarketagent.insight.InsightPeriod.startOf;

import com.supermarketagent.catalog.ProductCategory;
import com.supermarketagent.insight.CategoryProducts.Product;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Products behind one bar of "spending by category", over the same months as the dashboard. */
@Service
@Transactional(readOnly = true)
public class CategoryProductsService {

    public static final String UNCATEGORIZED = "none";

    private final JdbcTemplate jdbc;

    CategoryProductsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Empty when {@code category} is not a known category or {@code none}. */
    public Optional<CategoryProducts> products(long userId, String category, YearMonth currentMonth, int months) {
        boolean uncategorized = UNCATEGORIZED.equals(category);
        if (!uncategorized && !isKnown(category)) {
            return Optional.empty();
        }
        Timestamp from = Timestamp.from(startOf(currentMonth.minusMonths(months - 1L)));
        Timestamp to = Timestamp.from(endOf(currentMonth));

        // Items not matched to a product yet are grouped by their receipt description
        List<Product> products = jdbc.query("""
                WITH items AS (
                    SELECT p.id AS product_id,
                           COALESCE(p.normalized_name, sp.description) AS name,
                           COALESCE(s.display_name, s.name) AS store_name,
                           i.unit_price, i.total_price, r.issued_at, i.id AS item_id
                    FROM receipts r
                    JOIN stores s ON s.id = r.store_id
                    JOIN receipt_items i ON i.receipt_id = r.id
                    JOIN store_products sp ON sp.id = i.store_product_id
                    LEFT JOIN products p ON p.id = sp.product_id
                    WHERE r.user_id = ? AND r.issued_at >= ? AND r.issued_at < ?
                      AND (p.category = ? OR (? AND p.category IS NULL))
                ),
                ranked AS (
                    SELECT *, row_number() OVER (PARTITION BY product_id, name ORDER BY issued_at DESC, item_id DESC)
                              AS recency
                    FROM items
                )
                SELECT product_id, name, count(*) AS times_bought, sum(total_price) AS total_spent,
                       max(unit_price) FILTER (WHERE recency = 1) AS last_price,
                       max(store_name) FILTER (WHERE recency = 1) AS last_store,
                       max(issued_at) AS last_bought
                FROM ranked
                GROUP BY product_id, name
                ORDER BY total_spent DESC, name""",
                (rs, row) -> new Product(
                        (Long) rs.getObject("product_id"),
                        rs.getString("name"),
                        rs.getInt("times_bought"),
                        rs.getBigDecimal("total_spent"),
                        rs.getBigDecimal("last_price"),
                        rs.getString("last_store"),
                        rs.getTimestamp("last_bought").toInstant()),
                userId, from, to, uncategorized ? null : category, uncategorized);

        BigDecimal total = products.stream().map(Product::totalSpent).reduce(BigDecimal.ZERO, BigDecimal::add);
        String label = uncategorized
                ? SpendingInsightService.UNCATEGORIZED_LABEL
                : ProductCategory.valueOf(category).label();
        return Optional.of(new CategoryProducts(category, label, total, products));
    }

    private static boolean isKnown(String category) {
        for (ProductCategory known : ProductCategory.values()) {
            if (known.name().equals(category)) {
                return true;
            }
        }
        return false;
    }
}
