package com.supermarketagent.receipt.provider;

/** The QR code URL does not point to an official SEFAZ consultation page. */
public class UntrustedReceiptUrlException extends RuntimeException {

    public UntrustedReceiptUrlException(String message) {
        super(message);
    }
}
