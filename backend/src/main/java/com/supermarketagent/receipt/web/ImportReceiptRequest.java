package com.supermarketagent.receipt.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param qrCodeUrl the URL read from the NFC-e QR code */
public record ImportReceiptRequest(@NotBlank @Size(max = 1000) String qrCodeUrl) {
}
