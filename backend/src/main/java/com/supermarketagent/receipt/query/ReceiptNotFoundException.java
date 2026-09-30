package com.supermarketagent.receipt.query;

/** Also used when the receipt belongs to another user, so its existence is not revealed. */
public class ReceiptNotFoundException extends RuntimeException {

    public ReceiptNotFoundException(long id) {
        super("Receipt " + id + " not found");
    }
}
