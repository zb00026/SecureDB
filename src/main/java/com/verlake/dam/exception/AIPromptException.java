package com.verlake.dam.exception;

/**
 * Exception thrown when there are issues with AI prompt operations
 */
public class AIPromptException extends RuntimeException {
    
    public AIPromptException(String message) {
        super(message);
    }
    
    public AIPromptException(String message, Throwable cause) {
        super(message, cause);
    }
}
