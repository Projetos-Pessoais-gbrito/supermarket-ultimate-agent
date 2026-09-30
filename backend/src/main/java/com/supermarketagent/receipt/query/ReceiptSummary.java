package com.supermarketagent.receipt.query;

import java.math.BigDecimal;
import java.time.Instant;

public record ReceiptSummary(long id, String storeName, Instant issuedAt, BigDecimal totalAmount, int itemCount) {
}
