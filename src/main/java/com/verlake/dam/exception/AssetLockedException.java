package com.verlake.dam.exception;

/**
 * Exception thrown when attempting to access a locked asset
 */
public class AssetLockedException extends RuntimeException {
    
    public AssetLockedException(String message) {
        super(message);
    }
    
    public AssetLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}

