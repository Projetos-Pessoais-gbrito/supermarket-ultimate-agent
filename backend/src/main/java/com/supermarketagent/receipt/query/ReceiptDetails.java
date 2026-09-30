package com.supermarketagent.receipt.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReceiptDetails(
        long id,
        String accessKey,
        long number,
        int series,
        Instant issuedAt,
        Store store,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal approximateTaxes,
        List<Item> items,
        List<Payment> payments) {

    /** @param name display name (e.g. ASSAI); {@code legalName} as printed by SEFAZ */
    public record Store(long id, String cnpj, String name, String legalName, String address) {
    }

    public record Item(int lineNumber, String code, String description, BigDecimal quantity, String unit,
                       BigDecimal unitPrice, BigDecimal totalPrice) {
    }

    public record Payment(String method, BigDecimal amount) {
    }
}
