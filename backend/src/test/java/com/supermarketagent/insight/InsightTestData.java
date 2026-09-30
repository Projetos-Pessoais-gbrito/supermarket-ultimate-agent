package com.supermarketagent.insight;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

/** Builds users, stores, products and receipts directly in the database for insight tests. */
final class InsightTestData {

    private final JdbcTemplate jdbc;
    private final AtomicLong sequence = new AtomicLong();

    InsightTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset() {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, users "
                + "RESTART IDENTITY CASCADE");
    }

    long user(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES (?, 'hash') RETURNING id", Long.class, email);
    }

    long store(String name) {
        String cnpj = String.format("%014d", sequence.incrementAndGet());
        return jdbc.queryForObject("INSERT INTO stores (cnpj, name, state_code) VALUES (?, ?, '35') RETURNING id",
                Long.class, cnpj, name);
    }

    /** @param category enum name or null */
    long product(String name, String category) {
        return jdbc.queryForObject(
                "INSERT INTO products (normalized_name, category) VALUES (?, ?) RETURNING id", Long.class, name, category);
    }

    /** Store product linked to {@code productId} (may be null for unmatched). */
    long storeProduct(long storeId, Long productId, String description) {
        return jdbc.queryForObject("""
                INSERT INTO store_products (store_id, store_code, description, unit, product_id)
                VALUES (?, ?, ?, 'UN', ?) RETURNING id""",
                Long.class, storeId, "C" + sequence.incrementAndGet(), description, productId);
    }

    /** Receipt with a single item; the receipt total equals the item total. */
    long receipt(long userId, long storeId, String issuedAt, long storeProductId, String quantity, String unitPrice) {
        BigDecimal total = new BigDecimal(quantity).multiply(new BigDecimal(unitPrice));
        long receiptId = emptyReceipt(userId, storeId, issuedAt, total);
        item(receiptId, storeProductId, 1, quantity, unitPrice);
        return receiptId;
    }

    long emptyReceipt(long userId, long storeId, String issuedAt, BigDecimal total) {
        String key = "3526" + String.format("%040d", sequence.incrementAndGet());
        return jdbc.queryForObject("""
                INSERT INTO receipts (user_id, store_id, access_key, number, series, issued_at, total_amount,
                                      source_url, raw_html)
                VALUES (?, ?, ?, 1, 1, ?, ?, 'https://example', '<html/>') RETURNING id""",
                Long.class, userId, storeId, key, Timestamp.from(Instant.parse(issuedAt)), total);
    }

    void item(long receiptId, long storeProductId, int line, String quantity, String unitPrice) {
        BigDecimal total = new BigDecimal(quantity).multiply(new BigDecimal(unitPrice));
        jdbc.update("""
                INSERT INTO receipt_items (receipt_id, store_product_id, line_number, quantity, unit, unit_price,
                                           total_price)
                VALUES (?, ?, ?, ?, 'UN', ?, ?)""",
                receiptId, storeProductId, line, new BigDecimal(quantity), new BigDecimal(unitPrice), total);
    }
}
