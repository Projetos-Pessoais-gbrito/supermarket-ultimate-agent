package com.supermarketagent.user;

/** Password confirmation failed for a sensitive action (answered with 403, not 401, so it is not a session error). */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("Incorrect password");
    }
}
