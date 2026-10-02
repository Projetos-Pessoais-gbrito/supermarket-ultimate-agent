package com.supermarketagent.shopping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helps plan a shopping trip from the user's own receipts: products to add while typing, the markets
 * they shop at, and what each product on the list cost on their latest receipt from one market.
 */
@Service
@Transactional(readOnly = true)
public class ListPlanningService {

    static final int MAX_PRODUCT_OPTIONS = 8;
    private static final int MAX_SEARCH_WORDS = 5;

    private final JdbcTemplate jdbc;

    ListPlanningService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Products the user bought whose name contains every typed word (accents and case ignored), names
     * starting with the text first, then the most bought.
     */
    public List<ProductOption> searchProducts(long userId, String text) {
        List<String> words = Arrays.stream(text.strip().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .limit(MAX_SEARCH_WORDS)
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, COALESCE(p.display_name, p.normalized_name) AS name, count(DISTINCT r.id) AS purchases
                FROM products p
                JOIN store_products sp ON sp.product_id = p.id
                JOIN receipt_items i ON i.store_product_id = sp.id
                JOIN receipts r ON r.id = i.receipt_id
                WHERE r.user_id = ?""");
        List<Object> args = new ArrayList<>(List.of(userId));
        for (String word : words) {
            sql.append("""

                      AND (unaccent(lower(COALESCE(p.display_name, p.normalized_name))) LIKE unaccent(lower(?)) ESCAPE '\\'
                        OR unaccent(lower(sp.description)) LIKE unaccent(lower(?)) ESCAPE '\\')""");
            String pattern = "%" + escapeLike(word) + "%";
            args.add(pattern);
            args.add(pattern);
        }
        sql.append("""

                GROUP BY p.id, COALESCE(p.display_name, p.normalized_name)
                ORDER BY unaccent(lower(COALESCE(p.display_name, p.normalized_name))) LIKE unaccent(lower(?)) ESCAPE '\\' DESC,
                         purchases DESC, name
                LIMIT ?""");
        args.add(escapeLike(text.strip()) + "%");
        args.add(MAX_PRODUCT_OPTIONS);
        return jdbc.query(sql.toString(),
                (rs, row) -> new ProductOption(rs.getLong("id"), rs.getString("name")),
                args.toArray());
    }

    /** Markets the user has receipts from, most recent first; branches of a chain count as one. */
    public List<Market> markets(long userId) {
        return jdbc.query("""
                SELECT COALESCE(s.display_name, s.name) AS name, max(r.issued_at) AS last_purchase
                FROM receipts r JOIN stores s ON s.id = r.store_id
                WHERE r.user_id = ?
                GROUP BY COALESCE(s.display_name, s.name)
                ORDER BY last_purchase DESC, name""",
                (rs, row) -> new Market(rs.getString("name"), rs.getTimestamp("last_purchase").toInstant()),
                userId);
    }

    /**
     * For each product on the user's list, the unit price on their latest receipt from {@code market}
     * (any branch). Products never bought there are left out.
     */
    public ListPrices prices(long userId, String market) {
        List<ListPrice> prices = jdbc.query("""
                SELECT DISTINCT ON (sp.product_id) sp.product_id, i.unit_price, i.unit, r.issued_at
                FROM receipts r
                JOIN stores s ON s.id = r.store_id
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                WHERE r.user_id = ? AND COALESCE(s.display_name, s.name) = ?
                  AND sp.product_id IN (SELECT product_id FROM shopping_list_items
                                        WHERE user_id = ? AND product_id IS NOT NULL)
                ORDER BY sp.product_id, r.issued_at DESC, i.line_number""",
                (rs, row) -> new ListPrice(rs.getLong("product_id"), rs.getBigDecimal("unit_price"),
                        rs.getString("unit"), rs.getTimestamp("issued_at").toInstant()),
                userId, market, userId);
        return new ListPrices(market, prices);
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record ProductOption(long productId, String name) {
    }

    public record Market(String name, Instant lastPurchase) {
    }

    /** @param issuedAt when the receipt the price comes from was issued */
    public record ListPrice(long productId, BigDecimal unitPrice, String unit, Instant issuedAt) {
    }

    public record ListPrices(String market, List<ListPrice> items) {
    }
}
