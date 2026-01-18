package com.verlake.dam.controller.freshdesk;

import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.DatabaseSchemaDTO;
import com.verlake.dam.service.freshdesk.FreshdeskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Controller for Freshdesk integration
 * Provides OAuth authentication and API endpoints for Freshdesk sidebar app
 */
@RestController
@RequestMapping("/api/freshdesk")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Slf4j
public class FreshdeskController {

    private final FreshdeskService freshdeskService;

    public FreshdeskController(FreshdeskService freshdeskService) {
        this.freshdeskService = freshdeskService;
    }

    /**
     * Authenticate Freshdesk user as Hagrids developer
     * POST /api/freshdesk/auth
     * 
     * @param authRequest Contains Freshdesk user email and optional token
     * @return UserDTO with Hagrids authentication token
     */
    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody FreshdeskAuthRequest authRequest) {
        log.info("Freshdesk authentication request for email: {}", authRequest.getEmail());
        
        try {
            Map<String, Object> authResponse = freshdeskService.authenticateFreshdeskUser(
                    authRequest.getEmail(),
                    authRequest.getFreshdeskToken()
            );
            
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            log.error("Failed to authenticate Freshdesk user: {}", authRequest.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication failed: " + e.getMessage()
            );
        }
    }

    /**
     * Get all assets available to the authenticated developer
     * GET /api/freshdesk/assets
     * 
     * @param authorization Bearer token from Freshdesk authentication
     * @return List of assets
     */
    @GetMapping("/assets")
    public ResponseEntity<List<AssetDTO>> getAssets(@RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("Fetching assets for Freshdesk user");
        
        if (authorization == null || authorization.isEmpty()) {
            log.error("Authorization header is missing");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            );
        }
        
        try {
            String token = extractBearerToken(authorization);
            log.debug("Extracted token from Authorization header (length: {})", token.length());
            List<AssetDTO> assets = freshdeskService.getAssetsForDeveloper(token);
            log.info("Successfully fetched {} assets for Freshdesk user", assets.size());
            return ResponseEntity.ok(assets);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch assets for Freshdesk user", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch assets: " + e.getMessage()
            );
        }
    }

    /**
     * Get access requests for the authenticated developer
     * GET /api/freshdesk/access-requests
     * 
     * @param authorization Bearer token from Freshdesk authentication
     * @return List of access requests
     */
    @GetMapping("/access-requests")
    public ResponseEntity<List<Map<String, Object>>> getAccessRequests(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long assetId) {
        log.debug("Fetching access requests for Freshdesk user, assetId: {}", assetId);
        
        try {
            String token = extractBearerToken(authorization);
            List<Map<String, Object>> requests = freshdeskService.getAccessRequestsForDeveloper(token, assetId);
            return ResponseEntity.ok(requests);
        } catch (Exception e) {
            log.error("Failed to fetch access requests for Freshdesk user", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch access requests: " + e.getMessage()
            );
        }
    }

    /**
     * Get database schema for a specific access request
     * GET /api/freshdesk/schema
     * 
     * @param authorization Bearer token
     * @param requestId Access request ID
     * @return Database schema
     */
    @GetMapping("/schema")
    public ResponseEntity<DatabaseSchemaDTO> getSchema(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Long requestId) {
        log.debug("Fetching schema for requestId: {}", requestId);
        
        try {
            String token = extractBearerToken(authorization);
            DatabaseSchemaDTO schema = freshdeskService.getSchemaForRequest(token, requestId);
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            log.error("Failed to fetch schema for requestId: {}", requestId, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch schema: " + e.getMessage()
            );
        }
    }

    /**
     * Execute a database query
     * POST /api/freshdesk/run-query
     * 
     * @param authorization Bearer token
     * @param queryDto Query details
     * @return Query results
     */
    @PostMapping("/run-query")
    public ResponseEntity<Map<String, Object>> runQuery(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AccessQueryDTO queryDto) {
        log.info("Executing query for Freshdesk user, assetId: {}, requestId: {}", 
                queryDto.getAssetId(), queryDto.getRequestId());
        
        try {
            String token = extractBearerToken(authorization);
            Map<String, Object> results = freshdeskService.executeQuery(token, queryDto);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Failed to execute query for Freshdesk user", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to execute query: " + e.getMessage()
            );
        }
    }

    /**
     * Convert natural language to SQL
     * POST /api/freshdesk/convert-nl-to-sql
     * 
     * @param authorization Bearer token
     * @param request Natural language query request
     * @return SQL query
     */
    @PostMapping("/convert-nl-to-sql")
    public ResponseEntity<Map<String, Object>> convertNaturalLanguageToSql(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request) {
        log.info("Converting natural language to SQL for Freshdesk user");
        
        try {
            String token = extractBearerToken(authorization);
            Map<String, Object> result = freshdeskService.convertNaturalLanguageToSql(token, request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to convert natural language to SQL", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert query: " + e.getMessage()
            );
        }
    }

    /**
     * Health check endpoint for Freshdesk app
     * GET /api/freshdesk/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "freshdesk-integration"));
    }

    /**
     * Extract Bearer token from Authorization header
     */
    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authorization header");
        }
        return authorization.substring(7);
    }

    /**
     * Request DTO for Freshdesk authentication
     */
    public static class FreshdeskAuthRequest {
        private String email;
        private String freshdeskToken;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFreshdeskToken() {
            return freshdeskToken;
        }

        public void setFreshdeskToken(String freshdeskToken) {
            this.freshdeskToken = freshdeskToken;
        }
    }
}

