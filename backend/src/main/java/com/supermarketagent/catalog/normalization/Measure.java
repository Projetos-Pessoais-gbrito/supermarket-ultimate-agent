package com.supermarketagent.catalog.normalization;

import java.math.BigDecimal;
import java.util.Objects;

/** Package size in a base unit, e.g. 5KG → 5000 g, 6X1L → 6000 ml. */
public record Measure(BigDecimal amount, BaseUnit unit) {

    public enum BaseUnit {
        GRAM("g"),
        MILLILITER("ml");

        private final String symbol;

        BaseUnit(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    public Measure {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(unit);
        amount = amount.stripTrailingZeros();
    }
}
