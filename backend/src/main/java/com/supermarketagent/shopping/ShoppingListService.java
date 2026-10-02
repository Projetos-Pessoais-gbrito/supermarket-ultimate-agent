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

    /**
     * Adds the chosen lines of one of the user's receipts, with the quantities bought; repeated lines of a
     * product become one item. Lines without a catalog product come in as free text. Empty when the receipt
     * does not exist or belongs to someone else.
     */
    public Optional<AddedItems> addFromReceipt(long userId, long receiptId, List<Integer> lineNumbers) {
        Integer owned = jdbc.queryForObject("SELECT count(*) FROM receipts WHERE id = ? AND user_id = ?",
                Integer.class, receiptId, userId);
        if (owned == null || owned == 0) {
            return Optional.empty();
        }
        List<NewItem> lines = jdbc.query("""
                SELECT sp.product_id,
                       COALESCE(p.display_name, p.normalized_name, sp.description) AS name,
                       sum(i.quantity) AS quantity
                FROM receipt_items i
                JOIN store_products sp ON sp.id = i.store_product_id
                LEFT JOIN products p ON p.id = sp.product_id
                WHERE i.receipt_id = ? AND i.line_number = ANY (?)
                GROUP BY sp.product_id, COALESCE(p.display_name, p.normalized_name, sp.description)
                ORDER BY min(i.line_number)""",
                (rs, row) -> new NewItem((Long) rs.getObject("product_id"), rs.getString("name"),
                        rs.getBigDecimal("quantity")),
                receiptId, lineNumbers.toArray(Integer[]::new));
        return Optional.of(addAll(userId, lines));
    }

    /** Adds each item unless the same product (or free text) is already on the list to buy. */
    AddedItems addAll(long userId, List<NewItem> items) {
        int added = 0;
        for (NewItem item : items) {
            if (addIfAbsent(userId, item)) {
                added++;
            }
        }
        return new AddedItems(added, items.size() - added);
    }

    private boolean addIfAbsent(long userId, NewItem item) {
        if (item.productId() != null) {
            return jdbc.update("""
                    INSERT INTO shopping_list_items (user_id, product_id, name, quantity) VALUES (?, ?, ?, ?)
                    ON CONFLICT (user_id, product_id) WHERE product_id IS NOT NULL AND NOT checked DO NOTHING""",
                    userId, item.productId(), item.name(), item.quantity()) > 0;
        }
        return jdbc.update("""
                INSERT INTO shopping_list_items (user_id, name, quantity)
                SELECT ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM shopping_list_items
                                  WHERE user_id = ? AND product_id IS NULL AND NOT checked AND lower(name) = lower(?))""",
                userId, item.name(), item.quantity(), userId, item.name()) > 0;
    }

    /** An item to put on the list; {@code productId} null for free text. */
    record NewItem(Long productId, String name, BigDecimal quantity) {
    }

    /** @param alreadyInList items skipped because they were already on the list to buy */
    public record AddedItems(int added, int alreadyInList) {
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
