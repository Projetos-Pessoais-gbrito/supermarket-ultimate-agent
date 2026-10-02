package com.supermarketagent.shopping;

import com.supermarketagent.shopping.ShoppingListService.AddedItems;
import com.supermarketagent.shopping.ShoppingListService.NewItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Named copies of the user's shopping list to reuse later. Every query is scoped to the owner. */
@Service
@Transactional
public class SavedShoppingListService {

    private final JdbcTemplate jdbc;
    private final ShoppingListService shoppingList;

    SavedShoppingListService(JdbcTemplate jdbc, ShoppingListService shoppingList) {
        this.jdbc = jdbc;
        this.shoppingList = shoppingList;
    }

    /** Most recently saved first. */
    @Transactional(readOnly = true)
    public List<SavedShoppingList> list(long userId) {
        return jdbc.query("""
                SELECT l.id, l.name, l.updated_at, count(i.id) AS item_count
                FROM saved_shopping_lists l LEFT JOIN saved_shopping_list_items i ON i.list_id = l.id
                WHERE l.user_id = ?
                GROUP BY l.id
                ORDER BY l.updated_at DESC, l.id DESC""",
                (rs, row) -> new SavedShoppingList(rs.getLong("id"), rs.getString("name"), rs.getInt("item_count"),
                        rs.getTimestamp("updated_at").toInstant()),
                userId);
    }

    /**
     * Saves every item on the user's list (bought or not) under {@code name}, replacing a saved list with
     * the same name. Empty when the list has nothing to save.
     */
    public Optional<SavedShoppingList> saveCurrent(long userId, String name) {
        Integer items = jdbc.queryForObject("SELECT count(*) FROM shopping_list_items WHERE user_id = ?",
                Integer.class, userId);
        if (items == null || items == 0) {
            return Optional.empty();
        }
        long listId = jdbc.queryForObject("""
                INSERT INTO saved_shopping_lists (user_id, name) VALUES (?, ?)
                ON CONFLICT (user_id, (lower(name))) DO UPDATE SET name = EXCLUDED.name, updated_at = now()
                RETURNING id""", Long.class, userId, name.strip());
        jdbc.update("DELETE FROM saved_shopping_list_items WHERE list_id = ?", listId);
        jdbc.update("""
                INSERT INTO saved_shopping_list_items (list_id, product_id, name, quantity, position)
                SELECT ?, product_id, name, quantity, row_number() OVER (ORDER BY checked, created_at, id)
                FROM shopping_list_items WHERE user_id = ?""", listId, userId);
        return list(userId).stream().filter(saved -> saved.id() == listId).findFirst();
    }

    /** Adds the saved items to the user's list, skipping those already on it. Empty when not the user's. */
    public Optional<AddedItems> apply(long userId, long listId) {
        if (!owns(userId, listId)) {
            return Optional.empty();
        }
        List<NewItem> items = jdbc.query("""
                SELECT product_id, name, quantity FROM saved_shopping_list_items
                WHERE list_id = ? ORDER BY position""",
                (rs, row) -> new NewItem((Long) rs.getObject("product_id"), rs.getString("name"),
                        rs.getBigDecimal("quantity")),
                listId);
        return Optional.of(shoppingList.addAll(userId, items));
    }

    /** False when the list does not exist or belongs to someone else. */
    public boolean delete(long userId, long listId) {
        return jdbc.update("DELETE FROM saved_shopping_lists WHERE id = ? AND user_id = ?", listId, userId) > 0;
    }

    private boolean owns(long userId, long listId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM saved_shopping_lists WHERE id = ? AND user_id = ?",
                Integer.class, listId, userId);
        return count != null && count > 0;
    }

    public record SavedShoppingList(long id, String name, int itemCount, Instant updatedAt) {
    }
}
