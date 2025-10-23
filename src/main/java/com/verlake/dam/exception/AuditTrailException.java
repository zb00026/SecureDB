package com.verlake.dam.exception;

/**
 * Exception class for audit trail related operations
 * This exception is thrown when there are issues with audit trail processing,
 * statistics generation, or data export operations.
 */
public class AuditTrailException extends RuntimeException {
    
    private final String operation;
    private final String context;
    
    /**
     * Constructor with message
     * @param message the error message
     */
    public AuditTrailException(String message) {
        super(message);
        this.operation = null;
        this.context = null;
    }
    
    /**
     * Constructor with message and cause
     * @param message the error message
     * @param cause the underlying cause
     */
    public AuditTrailException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.context = null;
    }
    
    /**
     * Constructor with message, operation, and context
     * @param message the error message
     * @param operation the operation that failed
     * @param context the context where the failure occurred
     */
    public AuditTrailException(String message, String operation, String context) {
        super(message);
        this.operation = operation;
        this.context = context;
    }
    
    /**
     * Constructor with message, operation, context, and cause
     * @param message the error message
     * @param operation the operation that failed
     * @param context the context where the failure occurred
     * @param cause the underlying cause
     */
    public AuditTrailException(String message, String operation, String context, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.context = context;
    }
    
    /**
     * Get the operation that failed
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }
    
    /**
     * Get the context where the failure occurred
     * @return the context information
     */
    public String getContext() {
        return context;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(": ").append(getMessage());
        
        if (operation != null) {
            sb.append(" [Operation: ").append(operation).append("]");
        }
        
        if (context != null) {
            sb.append(" [Context: ").append(context).append("]");
        }
        
        return sb.toString();
    }
}
