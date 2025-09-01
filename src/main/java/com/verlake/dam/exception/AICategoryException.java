package com.verlake.dam.exception;

/**
 * Exception thrown when there are errors related to AI category operations
 */
public class AICategoryException extends RuntimeException {
    
    public AICategoryException(String message) {
        super(message);
    }
    
    public AICategoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
