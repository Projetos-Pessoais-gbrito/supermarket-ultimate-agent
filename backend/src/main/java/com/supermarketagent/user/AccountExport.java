package com.supermarketagent.user;

import com.supermarketagent.receipt.query.ReceiptDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Everything personal the app keeps about a user (LGPD right to data portability). */
public record AccountExport(Instant exportedAt, Account account, List<ReceiptDetails> receipts,
                            List<ShoppingListEntry> shoppingList, List<SavedShoppingList> savedShoppingLists,
                            List<Budget> budgets) {

    public record Account(long id, String email, Instant createdAt) {
    }

    public record ShoppingListEntry(String name, BigDecimal quantity, boolean checked, Instant createdAt) {
    }

    public record SavedShoppingList(String name, Instant updatedAt, List<SavedShoppingListItem> items) {
    }

    public record SavedShoppingListItem(String name, BigDecimal quantity) {
    }

    /** @param category null for the overall monthly limit */
    public record Budget(String category, BigDecimal monthlyLimit, Instant updatedAt) {
    }
}
