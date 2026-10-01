package com.supermarketagent.shopping;

import java.math.BigDecimal;

/** @param productId null for free-text items */
public record ShoppingListItem(long id, Long productId, String name, BigDecimal quantity, boolean checked) {
}
