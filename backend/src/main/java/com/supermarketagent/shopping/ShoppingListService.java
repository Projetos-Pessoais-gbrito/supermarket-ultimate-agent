package com.supermarketagent.shopping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user's own shopping list. Every query is scoped to the owner. */
@Service
@Transactional
public class ShoppingListService {

    private static final RowMapper<ShoppingListItem> ITEM = (rs, row) -> new ShoppingListItem(
            rs.getLong("id"),
            (Long) rs.getObject("product_id"),
            rs.getString("name"),
            rs.getBigDecimal("quantity"),
            rs.getBoolean("checked"));

    private final JdbcTemplate jdbc;

    ShoppingListService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Items still to buy first, then checked ones; oldest first within each group. */
    @Transactional(readOnly = true)
    public List<ShoppingListItem> list(long userId) {
        return jdbc.query("""
                SELECT id, product_id, name, quantity, checked FROM shopping_list_items
                WHERE user_id = ? ORDER BY checked, created_at, id""", ITEM, userId);
    }

    /**
     * Adds a product the user has bought (its name comes from the catalog) and returns the existing
     * item when it is already on the list. Empty when the user never bought the product.
     */
    public Optional<ShoppingListItem> addProduct(long userId, long productId, BigDecimal quantity) {
        List<String> names = jdbc.queryForList("""
                SELECT DISTINCT COALESCE(p.display_name, p.normalized_name)
                FROM products p
                JOIN store_products sp ON sp.product_id = p.id
                JOIN receipt_items i ON i.store_product_id = sp.id
                JOIN receipts r ON r.id = i.receipt_id
                WHERE p.id = ? AND r.user_id = ?""", String.class, productId, userId);
        if (names.isEmpty()) {
            return Optional.empty();
        }
        List<ShoppingListItem> open = jdbc.query("""
                SELECT id, product_id, name, quantity, checked FROM shopping_list_items
                WHERE user_id = ? AND product_id = ? AND NOT checked""", ITEM, userId, productId);
        if (!open.isEmpty()) {
            return Optional.of(open.getFirst());
        }
        return Optional.of(jdbc.queryForObject("""
                INSERT INTO shopping_list_items (user_id, product_id, name, quantity)
                VALUES (?, ?, ?, ?) RETURNING id, product_id, name, quantity, checked""",
                ITEM, userId, productId, names.getFirst(), quantity));
    }

    public ShoppingListItem addText(long userId, String name, BigDecimal quantity) {
        return jdbc.queryForObject("""
                INSERT INTO shopping_list_items (user_id, name, quantity)
                VALUES (?, ?, ?) RETURNING id, product_id, name, quantity, checked""",
                ITEM, userId, name.strip(), quantity);
    }

    /** Empty when the item does not exist or belongs to someone else. */
    public Optional<ShoppingListItem> setChecked(long userId, long itemId, boolean checked) {
        if (!checked) {
            // Unchecking must not create a second open item for the same product
            jdbc.update("""
                    DELETE FROM shopping_list_items o
                    USING shopping_list_items t
                    WHERE t.id = ? AND t.user_id = ? AND t.product_id IS NOT NULL
                      AND o.user_id = t.user_id AND o.product_id = t.product_id AND NOT o.checked AND o.id <> t.id""",
                    itemId, userId);
        }
        return jdbc.query("""
                UPDATE shopping_list_items SET checked = ? WHERE id = ? AND user_id = ?
                RETURNING id, product_id, name, quantity, checked""", ITEM, checked, itemId, userId)
                .stream().findFirst();
    }

    /** False when the item does not exist or belongs to someone else. */
    public boolean remove(long userId, long itemId) {
        return jdbc.update("DELETE FROM shopping_list_items WHERE id = ? AND user_id = ?", itemId, userId) > 0;
    }

    public int removeChecked(long userId) {
        return jdbc.update("DELETE FROM shopping_list_items WHERE user_id = ? AND checked", userId);
    }
}
