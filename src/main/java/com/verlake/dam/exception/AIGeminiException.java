package com.verlake.dam.exception;

/**
 * Exception thrown when Gemini AI operations fail
 */
public class AIGeminiException extends RuntimeException {
    
    public AIGeminiException(String message) {
        super(message);
    }
    
    public AIGeminiException(String message, Throwable cause) {
        super(message, cause);
    }
}
