package com.verlake.dam.controller.asset_owner;

import com.verlake.dam.entity.dto.unix.*;
import com.verlake.dam.service.unix.UnixGroupService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Controller for Unix group management operations
 */
@RestController
@RequestMapping("/api/asset_owner/unix-groups")
@RequiredArgsConstructor
@Slf4j
public class UnixGroupController {

    private final UnixGroupService unixGroupService;
    private final UserService userService;

    /**
     * Create a new Unix group
     */
    @PostMapping
    public ResponseEntity<UnixGroupDTO> createGroup(
            @Valid @RequestBody UnixGroupDTO createDto) {
        
        log.info("Creating Unix group: {} for asset: {}", 
                createDto.getGroupName(), createDto.getAssetId());

        Long userId = userService.getCurrentUser().getId();
        UnixGroupDTO group = unixGroupService.createGroup(createDto, userId, null);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    /**
     * Create a new Unix group with session ID
     */
    @PostMapping("/session/{sessionId}")
    public ResponseEntity<UnixGroupDTO> createGroupWithSession(
            @PathVariable String sessionId,
            @Valid @RequestBody UnixGroupDTO createDto,
            Authentication authentication) {
        
        log.info("Creating Unix group: {} for asset: {} with sessionId: {}", 
                createDto.getGroupName(), createDto.getAssetId(), sessionId);

        Long userId = userService.getCurrentUser().getId();
        UnixGroupDTO group = unixGroupService.createGroup(createDto, userId, sessionId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    /**
     * Get all groups with pagination and filtering
     */
    @GetMapping
    public Page<UnixGroupDTO> getAllGroups(UnixGroupFilter filter) {
        log.debug("Fetching Unix groups with filter: {}", filter);
        
        return unixGroupService.getAllGroups(filter);
    }

    /**
     * Get all groups for an asset (non-paginated for backward compatibility)
     */
    @GetMapping("/asset/{assetId}")
    public ResponseEntity<List<UnixGroupDTO>> getGroupsByAsset(@PathVariable Long assetId) {
        log.debug("Fetching Unix groups for asset: {}", assetId);
        
        List<UnixGroupDTO> groups = unixGroupService.getGroupsByAsset(assetId);
        return ResponseEntity.ok(groups);
    }

    /**
     * Get groups for an asset with pagination
     */
    @GetMapping("/asset/{assetId}/paginated")
    public ResponseEntity<Page<UnixGroupDTO>> getGroupsByAssetPaginated(
            @PathVariable Long assetId,
            Pageable pageable) {
        
        log.debug("Fetching Unix groups for asset: {} with pagination", assetId);
        
        Page<UnixGroupDTO> groups = unixGroupService.getGroupsByAsset(assetId, pageable);
        return ResponseEntity.ok(groups);
    }

    /**
     * Get a specific group by ID
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<UnixGroupDTO> getGroupById(@PathVariable Long groupId) {
        log.debug("Fetching Unix group by ID: {}", groupId);
        
        UnixGroupDTO group = unixGroupService.getGroupById(groupId);
        return ResponseEntity.ok(group);
    }

    /**
     * Get custom groups (non-system groups) for an asset with pagination
     */
    @GetMapping("/asset/{assetId}/custom")
    public ResponseEntity<Page<UnixGroupDTO>> getCustomGroupsByAsset(
            @PathVariable Long assetId,
            Pageable pageable) {
        
        log.debug("Fetching custom Unix groups for asset: {} with pagination", assetId);
        
        Page<UnixGroupDTO> groups = unixGroupService.getCustomGroupsByAsset(assetId, pageable);
        return ResponseEntity.ok(groups);
    }

    /**
     * Get system groups for an asset with pagination
     */
    @GetMapping("/asset/{assetId}/system")
    public ResponseEntity<Page<UnixGroupDTO>> getSystemGroupsByAsset(
            @PathVariable Long assetId,
            Pageable pageable) {
        
        log.debug("Fetching system Unix groups for asset: {} with pagination", assetId);
        
        Page<UnixGroupDTO> groups = unixGroupService.getSystemGroupsByAsset(assetId, pageable);
        return ResponseEntity.ok(groups);
    }

    /**
     * Search groups by name pattern with pagination
     */
    @GetMapping("/search")
    public ResponseEntity<Page<UnixGroupDTO>> searchGroups(
            @RequestParam String query,
            @RequestParam(required = false) Long assetId,
            Pageable pageable) {
        
        log.debug("Searching Unix groups with query: {} for asset: {}", query, assetId);
        
        Page<UnixGroupDTO> groups = unixGroupService.searchGroups(query, assetId, pageable);
        return ResponseEntity.ok(groups);
    }

    /**
     * Update an existing group
     */
    @PutMapping("/{groupId}")
    public ResponseEntity<UnixGroupDTO> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody UnixGroupDTO updateDto,
            Authentication authentication) {
        
        log.info("Updating Unix group: {}", groupId);

        Long userId = userService.getCurrentUser().getId();
        UnixGroupDTO group = unixGroupService.updateGroup(groupId, updateDto, userId, null);
        
        return ResponseEntity.ok(group);
    }

    /**
     * Update an existing group with session ID
     */
    @PutMapping("/{groupId}/session/{sessionId}")
    public ResponseEntity<UnixGroupDTO> updateGroupWithSession(
            @PathVariable Long groupId,
            @PathVariable String sessionId,
            @Valid @RequestBody UnixGroupDTO updateDto,
            Authentication authentication) {
        
        log.info("Updating Unix group: {} with sessionId: {}", groupId, sessionId);

        Long userId = userService.getCurrentUser().getId();
        UnixGroupDTO group = unixGroupService.updateGroup(groupId, updateDto, userId, sessionId);
        
        return ResponseEntity.ok(group);
    }

    /**
     * Delete a group
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable Long groupId) {
        log.info("Deleting Unix group: {}", groupId);
        
        unixGroupService.deleteGroup(groupId);
        return CommonUtils.getSuccessResponse();
    }

    /**
     * Synchronize groups with the server
     */
    @PostMapping("/sync/{assetId}")
    public ResponseEntity<UnixGroupDTO.SyncResult> syncGroupsWithServer(
            @PathVariable Long assetId,
            Authentication authentication) {
        
        log.info("Synchronizing Unix groups for asset: {}", assetId);
        
        Long userId = userService.getCurrentUser().getId();
        UnixGroupDTO.SyncResult syncResult = unixGroupService.syncGroupsWithServer(assetId, userId);
        
        return ResponseEntity.ok(syncResult);
    }

    /**
     * Get folder suggestions from the server
     */
    @PostMapping("/folder-suggestions/{assetId}")
    public ResponseEntity<List<UnixFolderSuggestion>> getFolderSuggestions(
            @PathVariable Long assetId,
            FolderSuggestionsRequestDTO request) {
        
        log.debug("Getting folder suggestions for asset: {} and path: {}", assetId, request.getPath());
        
        try {
            List<UnixFolderSuggestion> suggestions = unixGroupService.getDetailedFolderSuggestions(assetId, request.getPath());
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Failed to get folder suggestions for asset: {} and path: {}", assetId, request.getPath(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }
    

    /**
     * Apply ACL permissions to the server
     */
    @PostMapping("/{groupId}/apply-acl")
    public ResponseEntity<AclApplicationResultDTO> applyAclPermissions(@PathVariable Long groupId) {
        log.info("Applying ACL permissions for group: {}", groupId);
        
        try {
            AclApplicationResultDTO result = unixGroupService.applyAclPermissions(groupId);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to apply ACL permissions for group: {}", groupId, e);
            AclApplicationResultDTO errorResponse = AclApplicationResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .groupId(groupId)
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Apply ACL permissions to the server with session ID
     */
    @PostMapping("/{groupId}/apply-acl/session/{sessionId}")
    public ResponseEntity<AclApplicationResultDTO> applyAclPermissionsWithSession(
            @PathVariable Long groupId, 
            @PathVariable String sessionId) {
        log.info("Applying ACL permissions for group: {} with sessionId: {}", groupId, sessionId);
        
        try {
            AclApplicationResultDTO result = unixGroupService.applyAclPermissionsWithSession(groupId, sessionId);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to apply ACL permissions for group: {} with sessionId: {}", groupId, sessionId, e);
            AclApplicationResultDTO errorResponse = AclApplicationResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .groupId(groupId)
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Apply folder access permissions using existing WebSocket connection
     */
    @PostMapping("/folder-access")
    public ResponseEntity<Map<String, Object>> applyFolderAccessPermissions(
            @Valid @RequestBody FolderAccessRequestDTO request) {
        
        log.info("Applying folder access permissions: {}", request);
        
        try {
            unixGroupService.applyFolderAccessPermissions(request);
            return CommonUtils.getSuccessResponse();
        } catch (Exception e) {
            log.error("Failed to apply folder access permissions: {}", request, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, e.getMessage()));
        }
    }

    /**
     * Get groups with access to a specific folder path
     */
    @GetMapping("/folder-access/{folderPath}")
    public ResponseEntity<Page<UnixGroupDTO>> getGroupsWithFolderAccess(
            @PathVariable String folderPath,
            @RequestParam(required = false) Long assetId,
            Pageable pageable) {
        
        log.debug("Fetching groups with access to folder: {} for asset: {}", folderPath, assetId);
        
        Page<UnixGroupDTO> groups = unixGroupService.getGroupsWithFolderAccess(folderPath, assetId, pageable);
        return ResponseEntity.ok(groups);
    }

    /**
     * Get Unix group statistics for an asset
     */
    @GetMapping("/statistics/asset/{assetId}")
    public ResponseEntity<Map<String, Object>> getGroupStatistics(@PathVariable Long assetId) {
        log.debug("Getting Unix group statistics for asset: {}", assetId);
        
        Map<String, Object> statistics = unixGroupService.getGroupStatistics(assetId);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get Unix group statistics dashboard
     */
    @GetMapping("/statistics/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStatistics() {
        log.debug("Getting Unix group dashboard statistics");
        
        Map<String, Object> dashboard = unixGroupService.getDashboardStatistics();
        return ResponseEntity.ok(dashboard);
    }

}
