package com.supermarketagent.shopping;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Every purchase of a canonical product by one user, oldest first, in São Paulo local dates. */
@Component
class PurchaseHistory {

    record Purchase(long productId, String productName, LocalDate day, Instant issuedAt, BigDecimal quantity,
                    BigDecimal unitPrice, String storeName) {
    }

    private final JdbcTemplate jdbc;

    PurchaseHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Purchases grouped by product (iteration order: product id), each list oldest first. */
    Map<Long, List<Purchase>> byProduct(long userId) {
        List<Purchase> purchases = jdbc.query("""
                SELECT p.id AS product_id, COALESCE(p.display_name, p.normalized_name) AS product_name,
                       (r.issued_at AT TIME ZONE 'America/Sao_Paulo')::date AS day, r.issued_at,
                       i.quantity, i.unit_price, COALESCE(s.display_name, s.name) AS store_name
                FROM receipts r
                JOIN stores s ON s.id = r.store_id
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                JOIN products p ON p.id = sp.product_id
                WHERE r.user_id = ?
                ORDER BY p.id, r.issued_at, i.id""",
                (rs, row) -> new Purchase(
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getObject("day", LocalDate.class),
                        rs.getTimestamp("issued_at").toInstant(),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getString("store_name")),
                userId);
        return purchases.stream()
                .collect(Collectors.groupingBy(Purchase::productId, LinkedHashMap::new, Collectors.toList()));
    }
}
