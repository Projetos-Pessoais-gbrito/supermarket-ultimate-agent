package com.supermarketagent.receipt.provider;

/** The SEFAZ page could not be read, e.g. the layout changed or the receipt was cancelled. */
public class NfcePageParseException extends RuntimeException {

    public NfcePageParseException(String message) {
        super(message);
    }

    public NfcePageParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
