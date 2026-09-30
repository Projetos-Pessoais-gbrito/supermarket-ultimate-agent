package com.supermarketagent.catalog.normalization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/**
 * Price per kilogram or per litre, so different package sizes can be compared
 * (e.g. 400 g for R$ 1,99 = R$ 4,98/kg).
 */
public record UnitPrice(BigDecimal amount, Per per) {

    public enum Per {
        KILOGRAM,
        LITER
    }

    /**
     * @param receiptUnit unit printed on the receipt line ({@code KG}, {@code UN}, ...)
     * @param unitPrice   price for one {@code receiptUnit}
     * @param measure     package size from the description, if any
     */
    public static Optional<UnitPrice> of(String receiptUnit, BigDecimal unitPrice, Optional<Measure> measure) {
        String unit = receiptUnit.strip().toUpperCase(Locale.ROOT);
        if (unit.equals("KG")) {
            return Optional.of(new UnitPrice(scale(unitPrice), Per.KILOGRAM));
        }
        if (unit.equals("L") || unit.equals("LT")) {
            return Optional.of(new UnitPrice(scale(unitPrice), Per.LITER));
        }
        return measure
                .filter(size -> size.amount().signum() > 0)
                .map(size -> new UnitPrice(
                        // base units are g and ml; prices are per kg and per L
                        unitPrice.multiply(BigDecimal.valueOf(1000)).divide(size.amount(), 2, RoundingMode.HALF_UP),
                        size.unit() == Measure.BaseUnit.GRAM ? Per.KILOGRAM : Per.LITER));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
