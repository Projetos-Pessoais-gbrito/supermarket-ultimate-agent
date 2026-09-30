package com.supermarketagent.receipt.provider;

import com.supermarketagent.receipt.domain.AccessKey;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Receipt data as read from a SEFAZ consultation page, before persistence. */
public record ParsedReceipt(
        AccessKey accessKey,
        Store store,
        long number,
        int series,
        LocalDateTime issuedAt,
        List<Item> items,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal approximateTaxes,
        List<Payment> payments) {

    public ParsedReceipt {
        items = List.copyOf(items);
        payments = List.copyOf(payments);
    }

    public record Store(String cnpj, String name, String address) {
    }

    public record Item(
            int lineNumber,
            String code,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal totalPrice) {
    }

    public record Payment(String method, BigDecimal amount) {
    }
}
