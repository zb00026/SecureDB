package com.verlake.dam.exception;

public class AssetQueryChangeRequestNotFoundException extends RuntimeException {
    
    private final Long requestId;

    public AssetQueryChangeRequestNotFoundException(Long requestId) {
        super(String.format("AssetQueryChangeRequest not found with id: %d", requestId));
        this.requestId = requestId;
    }

    public AssetQueryChangeRequestNotFoundException(String message, Long requestId) {
        super(message);
        this.requestId = requestId;
    }

    public AssetQueryChangeRequestNotFoundException(String message, Long requestId, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
    }

    public Long getRequestId() {
        return requestId;
    }
} 