package com.supermarketagent.receipt.provider;

public class UnsupportedStateException extends RuntimeException {

    public UnsupportedStateException(String stateCode) {
        super("Receipts from state code " + stateCode + " are not supported yet");
    }
}
