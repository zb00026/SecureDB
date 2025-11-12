package com.verlake.dam.exception;

/**
 * Exception thrown when an access request has expired
 */
public class AccessRequestExpiredException extends RuntimeException {
    public AccessRequestExpiredException(String message) {
        super(message);
    }
    
    public AccessRequestExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}

