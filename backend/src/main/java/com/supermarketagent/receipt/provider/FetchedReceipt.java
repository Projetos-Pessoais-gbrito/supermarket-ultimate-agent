package com.supermarketagent.receipt.provider;

/**
 * Result of fetching a receipt from SEFAZ.
 *
 * @param receipt       the parsed data
 * @param sanitizedHtml the source page with the buyer's personal data already removed
 */
public record FetchedReceipt(ParsedReceipt receipt, String sanitizedHtml) {
}
