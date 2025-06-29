package com.verlake.dam.exception;

/**
 * Exception thrown when JWT token operations fail
 */
public class JwtTokenException extends RuntimeException {
    
    public JwtTokenException(String message) {
        super(message);
    }
    
    public JwtTokenException(String message, Throwable cause) {
        super(message, cause);
    }
} 