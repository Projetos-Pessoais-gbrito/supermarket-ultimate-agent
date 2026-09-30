package com.supermarketagent.receipt.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supermarketagent.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Runs the Flyway migrations on a real PostgreSQL and checks the key constraints. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ReceiptSchemaTest {

    private static final String KEY = "35260111222333000181650010000123451123456788";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesAFullReceipt() {
        long userId = insertUser("ana@example.com");
        long storeId = insertStore("11222333000181");
        long storeProductId = jdbc.queryForObject("""
                INSERT INTO store_products (store_id, store_code, description, unit)
                VALUES (?, '94794', 'PAO FRANCES CONG KG BALCAO', 'KG') RETURNING id""", Long.class, storeId);
        long receiptId = insertReceipt(userId, storeId, KEY);
        jdbc.update("""
                INSERT INTO receipt_items (receipt_id, store_product_id, line_number, quantity, unit, unit_price, total_price)
                VALUES (?, ?, 1, 0.136, 'KG', 17.99, 2.45)""", receiptId, storeProductId);
        jdbc.update("INSERT INTO receipt_payments (receipt_id, method, amount) VALUES (?, 'Cartão de Crédito', 2.45)",
                receiptId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM receipt_items WHERE receipt_id = ?", Integer.class, receiptId))
                .isEqualTo(1);
    }

    @Test
    void sameUserCannotImportTheSameReceiptTwice() {
        long userId = insertUser("ana@example.com");
        long storeId = insertStore("11222333000181");
        insertReceipt(userId, storeId, KEY);

        assertThatThrownBy(() -> insertReceipt(userId, storeId, KEY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersCanImportTheSameReceipt() {
        long storeId = insertStore("11222333000181");
        insertReceipt(insertUser("ana@example.com"), storeId, KEY);
        insertReceipt(insertUser("bia@example.com"), storeId, KEY);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM receipts", Integer.class)).isEqualTo(2);
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        insertUser("ana@example.com");

        assertThatThrownBy(() -> insertUser("ANA@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void trigramSimilarityIsAvailableForProductMatching() {
        Double similarity = jdbc.queryForObject(
                "SELECT similarity('ARROZ TIO JOAO 5KG', 'ARROZ T.JOAO TP1 5KG')", Double.class);

        assertThat(similarity).isGreaterThan(0.3);
    }

    private long insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES (?, 'hash') RETURNING id", Long.class, email);
    }

    private long insertStore(String cnpj) {
        return jdbc.queryForObject("""
                INSERT INTO stores (cnpj, name, address, state_code)
                VALUES (?, 'SUPERMERCADO EXEMPLO LTDA', 'RUA DAS FLORES, 100', '35') RETURNING id""", Long.class, cnpj);
    }

    private long insertReceipt(long userId, long storeId, String accessKey) {
        return jdbc.queryForObject("""
                INSERT INTO receipts (user_id, store_id, access_key, number, series, issued_at, total_amount,
                                      source_url, raw_html)
                VALUES (?, ?, ?, 12345, 1, '2026-01-15T10:30:00-03:00', 2.45, 'https://example', '<html/>')
                RETURNING id""", Long.class, userId, storeId, accessKey);
    }
}
