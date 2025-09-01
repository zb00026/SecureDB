package com.verlake.dam.exception;

/**
 * Exception thrown when AI masking policy operations fail
 */
public class AIMaskingPolicyException extends RuntimeException {
    
    public AIMaskingPolicyException(String message) {
        super(message);
    }
    
    public AIMaskingPolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
