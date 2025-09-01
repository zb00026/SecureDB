package com.verlake.dam.exception;

/**
 * Exception thrown when AI sensitive pattern operations fail
 */
public class AISensitivePatternException extends RuntimeException {
    
    public AISensitivePatternException(String message) {
        super(message);
    }
    
    public AISensitivePatternException(String message, Throwable cause) {
        super(message, cause);
    }
}
