package com.verlake.dam.exception;

/**
 * Custom exception for Unix group operations
 */
public class UnixGroupException extends RuntimeException {
    
    public UnixGroupException(String message) {
        super(message);
    }
    
    public UnixGroupException(String message, Throwable cause) {
        super(message, cause);
    }
}
