package com.supermarketagent.ai;

/** The AI provider is not configured, over quota, or failing; AI features should degrade gracefully. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
