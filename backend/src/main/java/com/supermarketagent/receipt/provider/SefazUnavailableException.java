package com.supermarketagent.receipt.provider;

/** SEFAZ could not be reached or refused the request; the import can be retried later. */
public class SefazUnavailableException extends RuntimeException {

    public SefazUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
