package com.verlake.dam.exception;

public class FirebaseConfigurationException extends RuntimeException {
    
    public FirebaseConfigurationException(String message) {
        super(message);
    }

    public FirebaseConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
} 