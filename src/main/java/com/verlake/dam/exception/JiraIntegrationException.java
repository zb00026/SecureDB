package com.verlake.dam.exception;

/**
 * Exception thrown when Jira integration operations fail (e.g. approval, webhook processing).
 */
public class JiraIntegrationException extends RuntimeException {

    public JiraIntegrationException(String message) {
        super(message);
    }

    public JiraIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
