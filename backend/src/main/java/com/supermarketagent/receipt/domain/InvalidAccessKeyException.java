package com.supermarketagent.receipt.domain;

public class InvalidAccessKeyException extends RuntimeException {

    public InvalidAccessKeyException(String message) {
        super(message);
    }
}
