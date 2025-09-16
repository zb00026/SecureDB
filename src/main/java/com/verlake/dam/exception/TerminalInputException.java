package com.verlake.dam.exception;

/**
 * Custom exception for terminal input operations
 */
public class TerminalInputException extends Exception {
    public TerminalInputException(String message) {
        super(message);
    }
    
    public TerminalInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
