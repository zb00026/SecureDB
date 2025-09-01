package com.verlake.dam.exception;

/**
 * Exception thrown when there are issues with AI chat operations
 */
public class AIChatException extends RuntimeException {
    
    public AIChatException(String message) {
        super(message);
    }
    
    public AIChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
