package com.supermarketagent.budget;

import java.math.BigDecimal;
import java.util.List;

/**
 * The user's monthly limits.
 *
 * @param overall    limit for all spending in a month; null when not set
 * @param categories limits per product category (enum name); empty when none
 */
public record BudgetSettings(BigDecimal overall, List<CategoryLimit> categories) {

    public BudgetSettings {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /** @param label pt-BR category name, filled in responses */
    public record CategoryLimit(String category, String label, BigDecimal limit) {
    }
}
