package com.verlake.dam.controller;

import com.verlake.dam.entity.assets.dto.DatabaseSchemaDTO;
import com.verlake.dam.service.assets.DatabaseSchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schema")
@Slf4j
public class SchemaController {

    private final DatabaseSchemaService databaseSchemaService;

    public SchemaController(DatabaseSchemaService databaseSchemaService) {
        this.databaseSchemaService = databaseSchemaService;
    }

    /**
     * Get database schema for the logged-in user
     * 
     * @param assetId Asset ID (required for asset owners)
     * @param requestId Access request ID (required for accessors)
     * @param isAssetOwner true if requesting as asset owner, false if as accessor (optional, auto-detected if not provided)
     * @return Database schema filtered by user's permissions
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DatabaseSchemaDTO> getSchema(
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Long requestId,
            @RequestParam(required = false) Boolean isAssetOwner) {
        
        log.debug("Fetching database schema - assetId: {}, requestId: {}, isAssetOwner: {}", assetId, requestId, isAssetOwner);
        
        try {
            DatabaseSchemaDTO schema = databaseSchemaService.getSchemaForCurrentUser(assetId, requestId, isAssetOwner);
            log.info("Successfully fetched schema with {} tables and {} total columns", 
                    schema.getTotalTables(), schema.getTotalColumns());
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            log.error("Failed to fetch database schema", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get database schema by asset ID (supports both asset owners and accessors)
     * 
     * @param assetId Asset ID
     * @param isAssetOwner true if requesting as asset owner, false if as accessor (optional, auto-detected if not provided)
     */
    @GetMapping("/asset/{assetId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DatabaseSchemaDTO> getSchemaByAssetId(
            @PathVariable Long assetId,
            @RequestParam(required = false) Boolean isAssetOwner) {
        log.debug("Fetching database schema for asset ID: {}, isAssetOwner: {}", assetId, isAssetOwner);
        
        try {
            DatabaseSchemaDTO schema = databaseSchemaService.getSchemaForCurrentUser(assetId, null, isAssetOwner);
            log.info("Successfully fetched schema for asset {} with {} tables and {} total columns", 
                    assetId, schema.getTotalTables(), schema.getTotalColumns());
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            log.error("Failed to fetch database schema for asset ID: {}", assetId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get database schema by access request ID (for accessors)
     * requestId explicitly indicates accessor access, so isAssetOwner is ignored
     */
    @GetMapping("/request/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DatabaseSchemaDTO> getSchemaByRequestId(@PathVariable Long requestId) {
        log.debug("Fetching database schema for request ID: {}", requestId);
        
        try {
            // requestId explicitly indicates accessor path, ignore isAssetOwner
            DatabaseSchemaDTO schema = databaseSchemaService.getSchemaForCurrentUser(null, requestId, false);
            log.info("Successfully fetched schema for request {} with {} tables and {} total columns", 
                    requestId, schema.getTotalTables(), schema.getTotalColumns());
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            log.error("Failed to fetch database schema for request ID: {}", requestId, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
