package com.verlake.dam.exception;

/**
 * Exception thrown when an access request is not found
 */
public class AccessRequestNotFoundException extends RuntimeException {
    
    public AccessRequestNotFoundException(String message) {
        super(message);
    }
    
    public AccessRequestNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

