package com.supermarketagent.shopping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

/** Builds users, stores, products and receipts directly in the database for shopping tests. */
final class ShoppingTestData {

    private final JdbcTemplate jdbc;
    private final AtomicLong sequence = new AtomicLong();

    ShoppingTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset() {
        jdbc.execute("TRUNCATE shopping_list_items, receipt_payments, receipt_items, receipts, store_products, "
                + "products, stores, users RESTART IDENTITY CASCADE");
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

    long product(String name) {
        return jdbc.queryForObject("INSERT INTO products (normalized_name) VALUES (?) RETURNING id", Long.class, name);
    }

    /** Store product linked to {@code productId} (may be null for unmatched). */
    long storeProduct(long storeId, Long productId) {
        return jdbc.queryForObject("""
                INSERT INTO store_products (store_id, store_code, description, unit, product_id)
                VALUES (?, ?, ?, 'UN', ?) RETURNING id""",
                Long.class, storeId, "C" + sequence.incrementAndGet(), "ITEM " + sequence.get(), productId);
    }

    /** Store of a chain: SEFAZ shows the legal name, the app the brand. */
    long store(String legalName, String brand) {
        long id = store(legalName);
        jdbc.update("UPDATE stores SET display_name = ? WHERE id = ?", brand, id);
        return id;
    }

    /** Receipt with a single item bought at noon (São Paulo) of {@code day}. */
    void buy(long userId, long storeId, String day, long storeProductId, String quantity, String unitPrice) {
        receipt(userId, storeId, day, new Line(storeProductId, quantity, unitPrice));
    }

    /** Receipt bought at noon (São Paulo) of {@code day}; lines are numbered from 1 in order. */
    long receipt(long userId, long storeId, String day, Line... lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) {
            total = total.add(line.total());
        }
        String key = "3526" + String.format("%040d", sequence.incrementAndGet());
        long receiptId = jdbc.queryForObject("""
                INSERT INTO receipts (user_id, store_id, access_key, number, series, issued_at, total_amount,
                                      source_url, raw_html)
                VALUES (?, ?, ?, 1, 1, ?, ?, 'https://example', '<html/>') RETURNING id""",
                Long.class, userId, storeId, key, Timestamp.from(Instant.parse(day + "T15:00:00Z")), total);
        for (int i = 0; i < lines.length; i++) {
            Line line = lines[i];
            jdbc.update("""
                    INSERT INTO receipt_items (receipt_id, store_product_id, line_number, quantity, unit, unit_price,
                                               total_price)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    receiptId, line.storeProductId(), i + 1, new BigDecimal(line.quantity()), line.unit(),
                    new BigDecimal(line.unitPrice()), line.total());
        }
        return receiptId;
    }

    record Line(long storeProductId, String quantity, String unitPrice, String unit) {

        Line(long storeProductId, String quantity, String unitPrice) {
            this(storeProductId, quantity, unitPrice, "UN");
        }

        BigDecimal total() {
            return new BigDecimal(quantity).multiply(new BigDecimal(unitPrice)).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
