package com.verlake.dam.exception;

/**
 * Exception thrown when CSV export operations fail
 */
public class CsvExportException extends RuntimeException {
    
    public CsvExportException(String message) {
        super(message);
    }
    
    public CsvExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
