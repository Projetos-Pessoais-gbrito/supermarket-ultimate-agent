package com.supermarketagent.receipt.importing;

/**
 * @param receiptId the stored receipt
 * @param created   {@code false} when the user had already imported this receipt
 */
public record ReceiptImportResult(long receiptId, boolean created) {
}
