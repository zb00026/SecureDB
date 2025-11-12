package com.verlake.dam.service.unix;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.dto.unix.*;
import com.verlake.dam.entity.unix.AclPermission;
import com.verlake.dam.entity.unix.UnixGroup;
import com.verlake.dam.entity.unix.UnixGroupFolderAccess;
import com.verlake.dam.entity.unix.UnixGroupInfo;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.terminal.TerminalSession;
import com.verlake.dam.repository.unix.UnixGroupRepository;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.terminal.SSHConnectionService;
import com.verlake.dam.service.terminal.TerminalService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.UnixCommandBuilder;
import com.verlake.dam.exception.UnixGroupException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Unified service for managing Unix groups, SSH operations, and validation
 * Consolidates functionality from UnixGroupService, UnixGroupSSHService, and UnixGroupValidationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UnixGroupService {

    private final UnixGroupRepository unixGroupRepository;
    private final AssetService assetService;
    private final UserService userService;
    private final SSHConnectionService sshConnectionService;
    private final TerminalService terminalService;
    private final KeycloakService keycloakService;

    // Validation constants
    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern FOLDER_PATH_PATTERN = Pattern.compile("^/[a-zA-Z0-9._/-]*$");
    private static final int MAX_GROUP_NAME_LENGTH = 32;
    private static final int MAX_FOLDER_PATH_LENGTH = 1000;
    
    // Error message constants
    private static final String GROUP_NOT_FOUND_MSG = "Group not found: ";
    private static final String APPLIED_ACL_COMMAND_MSG = "Applied ACL command: {}";
    private static final String FAILED_SYNC_GROUPS_MSG = "Failed to synchronize groups: ";
    private static final String FAILED_REMOVE_ACL_MSG = "Failed to remove ACL permissions: ";
    
    // Path constants
    private static final String ROOT_PATH = "/";
    private static final String PATH_SEPARATOR = "/";

    /**
     * Create a new Unix group
     */
    @CacheEvict(value = "unixGroups", key = "#createDto.assetId")
    public UnixGroupDTO createGroup(UnixGroupDTO createDto, Long userId) {
        return createGroup(createDto, userId, null);
    }

    /**
     * Create a new Unix group with optional session ID for ACL application
     */
    @CacheEvict(value = "unixGroups", key = "#createDto.assetId")
    public UnixGroupDTO createGroup(UnixGroupDTO createDto, Long userId, String sessionId) {
        log.info("Creating Unix group: {} for asset: {}", createDto.getGroupName(), createDto.getAssetId());

        // Validate asset access
        Asset asset = assetService.findById(createDto.getAssetId());
        User user = userService.findById(userId);

        // Check if group already exists
        if (unixGroupRepository.existsByGroupNameAndAssetId(createDto.getGroupName(), createDto.getAssetId())) {
            throw new IllegalArgumentException("Group already exists: " + createDto.getGroupName());
        }

        // Create the group
        UnixGroup unixGroup = UnixGroup.builder()
                .asset(asset)
                .groupName(createDto.getGroupName())
                .description(createDto.getDescription())
                .isSystemGroup(createDto.getIsSystemGroup())
                .createdBy(user)
                .build();

        // Save the group
        unixGroup = unixGroupRepository.save(unixGroup);

        // Add folder accesses if provided
        if (createDto.getFolderAccesses() != null && !createDto.getFolderAccesses().isEmpty()) {
            for (UnixGroupDTO.FolderAccessDTO folderAccessDto : createDto.getFolderAccesses()) {
                if (folderAccessDto.isValidFolderPath()) {
                    // Normalize permissions (convert individual permissions to accessType if needed)
                    folderAccessDto.normalizePermissions();
                    
                    UnixGroupFolderAccess folderAccess = UnixGroupFolderAccess.builder()
                            .unixGroup(unixGroup)
                            .folderPath(folderAccessDto.getNormalizedFolderPath())
                            .accessType(folderAccessDto.getAccessType())
                            .permissions(folderAccessDto.getAccessType().getPermissionString())
                            .recursive(folderAccessDto.getRecursive())
                            .createdBy(user)
                            .build();

                    unixGroup.addFolderAccess(folderAccess);
                }
            }
        }

        // Save the group with folder accesses
        unixGroup = unixGroupRepository.save(unixGroup);

        // Apply ACL permissions to the server if folder accesses exist
        if (unixGroup.getFolderAccesses() != null && !unixGroup.getFolderAccesses().isEmpty()) {
            try {
                log.info("Applying ACL permissions for newly created group: {}", unixGroup.getGroupName());
                applyAclPermissionsToServer(unixGroup, sessionId);
                log.info("Successfully applied ACL permissions for group: {}", unixGroup.getGroupName());
            } catch (Exception e) {
                log.error("Failed to apply ACL permissions for newly created group: {}", unixGroup.getGroupName(), e);
                // Don't fail the creation if ACL application fails - just log the error
            }
        }

        log.info("Successfully created Unix group: {} with ID: {}", unixGroup.getGroupName(), unixGroup.getId());
        return UnixGroupDTO.fromEntity(unixGroup);
    }

    /**
     * Get all groups for an asset
     */
    @Cacheable(value = "unixGroups", key = "#assetId")
    public List<UnixGroupDTO> getGroupsByAsset(Long assetId) {
        log.debug("Fetching Unix groups for asset: {}", assetId);
        
        List<UnixGroup> groups = unixGroupRepository.findByAssetIdOrderByGroupNameAsc(assetId);
        return groups.stream()
                .map(UnixGroupDTO::fromEntity)
                .toList();
    }

    /**
     * Get groups for an asset with pagination
     */
    public Page<UnixGroupDTO> getGroupsByAsset(Long assetId, Pageable pageable) {
        log.debug("Fetching Unix groups for asset: {} with pagination", assetId);
        
        Page<UnixGroup> groups = unixGroupRepository.findByAssetIdOrderByGroupNameAsc(assetId, pageable);
        return groups.map(UnixGroupDTO::fromEntity);
    }

    /**
     * Get all groups with filtering and pagination
     */
    public Page<UnixGroupDTO> getAllGroups(UnixGroupFilter filter) {
        log.debug("Fetching Unix groups with filter: {}", filter);
        
        Specification<UnixGroup> spec = filter.toSpecification();
        Pageable pageable = filter.toPageRequest();
        
        Page<UnixGroup> groups = unixGroupRepository.findAll(spec, pageable);
        return groups.map(UnixGroupDTO::fromEntity);
    }

    /**
     * Get custom groups (non-system groups) for an asset with pagination
     */
    public Page<UnixGroupDTO> getCustomGroupsByAsset(Long assetId, Pageable pageable) {
        log.debug("Fetching custom Unix groups for asset: {} with pagination", assetId);
        
        Page<UnixGroup> groups = unixGroupRepository.findByAssetIdAndIsSystemGroupFalseOrderByGroupNameAsc(assetId, pageable);
        return groups.map(UnixGroupDTO::fromEntity);
    }

    /**
     * Get system groups for an asset with pagination
     */
    public Page<UnixGroupDTO> getSystemGroupsByAsset(Long assetId, Pageable pageable) {
        log.debug("Fetching system Unix groups for asset: {} with pagination", assetId);
        
        Page<UnixGroup> groups = unixGroupRepository.findByAssetIdAndIsSystemGroupTrueOrderByGroupNameAsc(assetId, pageable);
        return groups.map(UnixGroupDTO::fromEntity);
    }

    /**
     * Search groups by name pattern with pagination
     */
    public Page<UnixGroupDTO> searchGroups(String query, Long assetId, Pageable pageable) {
        log.debug("Searching Unix groups with query: {} for asset: {}", query, assetId);
        
        List<UnixGroup> groups;
        if (assetId != null) {
            groups = unixGroupRepository.findByAssetIdAndGroupNameContainingIgnoreCase(assetId, query);
        } else {
            // If no assetId specified, search across all assets
            groups = unixGroupRepository.findAll().stream()
                    .filter(group -> group.getGroupName().toLowerCase().contains(query.toLowerCase()))
                    .toList();
        }
        
        // Convert to Page
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), groups.size());
        List<UnixGroup> pageContent = groups.subList(start, end);
        
        return new PageImpl<>(
            pageContent.stream().map(UnixGroupDTO::fromEntity).toList(),
            pageable,
            groups.size()
        );
    }

    /**
     * Get groups with access to a specific folder path
     */
    public Page<UnixGroupDTO> getGroupsWithFolderAccess(String folderPath, Long assetId, Pageable pageable) {
        log.debug("Fetching groups with access to folder: {} for asset: {}", folderPath, assetId);
        
        List<UnixGroup> groups;
        if (assetId != null) {
            groups = unixGroupRepository.findGroupsWithAccessToFolder(assetId, folderPath);
        } else {
            // If no assetId specified, search across all assets
            groups = unixGroupRepository.findAll().stream()
                    .filter(group -> group.getFolderAccesses().stream()
                            .anyMatch(access -> access.getFolderPath().contains(folderPath)))
                    .toList();
        }
        
        // Convert to Page
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), groups.size());
        List<UnixGroup> pageContent = groups.subList(start, end);
        
        return new PageImpl<>(
            pageContent.stream().map(UnixGroupDTO::fromEntity).toList(),
            pageable,
            groups.size()
        );
    }

    /**
     * Get a specific group by ID
     */
    public UnixGroupDTO getGroupById(Long groupId) {
        log.debug("Fetching Unix group by ID: {}", groupId);
        
        UnixGroup group = unixGroupRepository.findByIdWithFolderAccesses(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND_MSG + groupId));
        
        return UnixGroupDTO.fromEntity(group);
    }

    /**
     * Update an existing group
     */
    @CacheEvict(value = "unixGroups", key = "#groupId")
    public UnixGroupDTO updateGroup(Long groupId, UnixGroupDTO updateDto, Long userId) {
        return updateGroup(groupId, updateDto, userId, null);
    }

    /**
     * Update an existing group with optional session ID for ACL application
     */
    @CacheEvict(value = "unixGroups", key = "#groupId")
    public UnixGroupDTO updateGroup(Long groupId, UnixGroupDTO updateDto, Long userId, String sessionId) {
        log.info("Updating Unix group: {}", groupId);

        UnixGroup group = unixGroupRepository.findByIdWithFolderAccesses(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND_MSG + groupId));

        // Update group properties
        if (updateDto.getDescription() != null) {
            group.setDescription(updateDto.getDescription());
        }

        // Update folder accesses if provided
        if (updateDto.getFolderAccesses() != null) {
            // Clear existing folder accesses
            group.getFolderAccesses().clear();
            
            // Add new folder accesses
            User user = userService.findById(userId);
            for (UnixGroupDTO.FolderAccessDTO folderAccessDto : updateDto.getFolderAccesses()) {
                if (folderAccessDto.isValidFolderPath()) {
                    // Normalize permissions (convert individual permissions to accessType if needed)
                    folderAccessDto.normalizePermissions();
                    
                    UnixGroupFolderAccess folderAccess = UnixGroupFolderAccess.builder()
                            .unixGroup(group)
                            .folderPath(folderAccessDto.getNormalizedFolderPath())
                            .accessType(folderAccessDto.getAccessType())
                            .permissions(folderAccessDto.getAccessType().getPermissionString())
                            .recursive(folderAccessDto.getRecursive())
                            .createdBy(user)
                            .build();

                    group.addFolderAccess(folderAccess);
                }
            }
        }

        // Save the updated group
        group = unixGroupRepository.save(group);

        // Apply ACL permissions to the server if folder accesses exist
        if (group.getFolderAccesses() != null && !group.getFolderAccesses().isEmpty()) {
            try {
                log.info("Applying ACL permissions for updated group: {}", group.getGroupName());
                applyAclPermissionsToServer(group, sessionId);
                log.info("Successfully applied ACL permissions for updated group: {}", group.getGroupName());
            } catch (Exception e) {
                log.error("Failed to apply ACL permissions for updated group: {}", group.getGroupName(), e);
                // Don't fail the update if ACL application fails - just log the error
            }
        }

        log.info("Successfully updated Unix group: {}", groupId);
        return UnixGroupDTO.fromEntity(group);
    }

    /**
     * Delete a group
     */
    @CacheEvict(value = "unixGroups", key = "#groupId")
    public void deleteGroup(Long groupId) {
        log.info("Deleting Unix group: {}", groupId);

        UnixGroup group = unixGroupRepository.findByIdWithFolderAccesses(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND_MSG + groupId));

        // Remove ACL permissions from the server
        try {
            removeAclPermissionsFromServer(group);
        } catch (Exception e) {
            log.error("Failed to remove ACL permissions from server for group: {}", groupId, e);
            // Continue with deletion even if server cleanup fails
        }

        // Delete the group (cascade will handle folder accesses)
        unixGroupRepository.delete(group);

        log.info("Successfully deleted Unix group: {}", groupId);
    }

    /**
     * Synchronize groups with the server
     */
    @CacheEvict(value = "unixGroups", key = "#assetId")
    public UnixGroupDTO.SyncResult syncGroupsWithServer(Long assetId, Long userId) {
        log.info("Synchronizing Unix groups for asset: {}", assetId);

        Asset asset = assetService.findById(assetId);
        User user = userService.findById(userId);

        try {
            // Get groups from server
            List<UnixGroupDTO> serverGroups = getGroupsFromServerAsDTO(asset);
            
            // Get local groups
            List<UnixGroup> localGroups = unixGroupRepository.findByAssetIdOrderByGroupNameAsc(assetId);
            
            // Perform synchronization
            UnixGroupDTO.SyncResult syncResult = performGroupSync(asset, serverGroups, localGroups, user);
            
            // Update sync timestamps
            updateSyncTimestamps(assetId);

            return syncResult;

        } catch (Exception e) {
            log.error("Failed to synchronize groups for asset: {}", assetId, e);
            throw new UnixGroupException(FAILED_SYNC_GROUPS_MSG + e.getMessage(), e);
        }
    }

    /**
     * Get folder suggestions from the server using existing WebSocket connection
     */
    public List<UnixFolderSuggestion> getFolderSuggestions(Long assetId, String path) {
        log.debug("Getting folder suggestions for asset: {} and path: {}", assetId, path);

        Asset asset = assetService.findById(assetId);

        try {
            // Try to use existing WebSocket connection first
            return getDetailedFolderSuggestions(asset, path);
        } catch (IOException e) {
            log.warn("No active terminal session found for asset: {}, falling back to new SSH connection", assetId);
            try {
                // Fall back to creating a new SSH connection
                return getDetailedFolderSuggestions(asset, path);
            } catch (Exception fallbackException) {
                log.error("Failed to get folder suggestions for asset: {}", assetId, fallbackException);
                return Collections.emptyList();
            }
        }
    }


    /**
     * Apply ACL permissions to the server with optional session ID
     */
    private void applyAclPermissionsToServer(UnixGroup group, String sessionId) throws IOException {
        log.debug("Applying ACL permissions for group: {} on asset: {} with sessionId: {}", 
                group.getGroupName(), group.getAsset().getId(), sessionId);

        Asset asset = group.getAsset();

        // Check if group exists on server, create if it doesn't
        if (!doesGroupExistOnServer(asset, group.getGroupName(), sessionId)) {
            log.info("Group {} does not exist on server, creating it first", group.getGroupName());
            createGroupOnServer(asset, group.getGroupName(), group.getDescription(), sessionId);
        }

        for (UnixGroupFolderAccess folderAccess : group.getFolderAccesses()) {
            String command = folderAccess.getAclCommand(group.getGroupName());
            
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                // Try to use existing WebSocket session first
                try {
                    log.debug("Using existing WebSocket session: {} for ACL command: {}", sessionId, command);
                    executeSSHCommandWithSession(asset, command, sessionId);
                } catch (Exception e) {
                    log.warn("Failed to use existing session {}, falling back to new SSH connection: {}", sessionId, e.getMessage());
                    executeSSHCommand(asset, command);
                }
            } else {
                // No session ID provided, use regular SSH connection
                executeSSHCommand(asset, command);
            }
            
            log.debug(APPLIED_ACL_COMMAND_MSG, command);
        }
    }

    /**
     * Apply ACL permissions to the server (public method for manual application)
     */
    public AclApplicationResultDTO applyAclPermissions(Long groupId) {
        return applyAclPermissionsWithSession(groupId, null);
    }

    /**
     * Apply ACL permissions to the server with session ID
     */
    public AclApplicationResultDTO applyAclPermissionsWithSession(Long groupId, String sessionId) {
        log.info("Applying ACL permissions for group ID: {}", groupId);
        
        // Fetch the group with folder accesses
        UnixGroup group = unixGroupRepository.findByIdWithFolderAccesses(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND_MSG + groupId));
        
        log.info("Applying ACL permissions for group: {} on asset: {}", 
                group.getGroupName(), group.getAsset().getId());

        Asset asset = group.getAsset();
        List<AclApplicationResultDTO.PermissionApplicationResult> appliedPermissions = new ArrayList<>();

        try {
            // Use the internal method to apply permissions with session ID
            applyAclPermissionsToServer(group, sessionId);
            
            // Build detailed results for the response
            for (UnixGroupFolderAccess folderAccess : group.getFolderAccesses()) {
                String command = folderAccess.getAclCommand(group.getGroupName());
                
                AclApplicationResultDTO.PermissionApplicationResult permissionResult = 
                    AclApplicationResultDTO.PermissionApplicationResult.builder()
                        .folderPath(folderAccess.getFolderPath())
                        .accessType(folderAccess.getAccessType().name())
                        .permissions(folderAccess.getPermissions())
                        .recursive(folderAccess.getRecursive())
                        .command(command)
                        .output("Applied successfully") // Since we already applied it
                        .success(true)
                        .build();
                
                appliedPermissions.add(permissionResult);
                log.debug(APPLIED_ACL_COMMAND_MSG, command);
            }
            
            return AclApplicationResultDTO.builder()
                    .success(true)
                    .groupId(group.getId())
                    .groupName(group.getGroupName())
                    .assetId(asset.getId())
                    .appliedPermissions(appliedPermissions)
                    .totalPermissionsApplied(appliedPermissions.size())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to apply ACL permissions for group: {}", group.getGroupName(), e);
            return AclApplicationResultDTO.builder()
                    .success(false)
                    .groupId(group.getId())
                    .groupName(group.getGroupName())
                    .assetId(asset.getId())
                    .error(e.getMessage())
                    .appliedPermissions(appliedPermissions)
                    .build();
        }
    }

    /**
     * Apply folder access permissions using existing WebSocket connection
     */
    public void applyFolderAccessPermissions(FolderAccessRequestDTO request) throws IOException {
        log.debug("Applying folder access permissions: {}", request);

        Asset asset = assetService.findById(request.getAssetId());
        if (asset == null) {
            throw new IllegalArgumentException("Asset not found: " + request.getAssetId());
        }

        UnixGroup group = unixGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND_MSG + request.getGroupId()));

        try {
            // Try to use existing WebSocket connection first
            applyFolderPermissionsOnActiveSession(group, request);
        } catch (IOException e) {
            log.warn("No active terminal session found for asset: {}, falling back to new SSH connection", request.getAssetId());
            // Fall back to creating a new SSH connection
            applyFolderPermissionsOnNewConnection(asset, group, request);
        }
    }

    /**
     * Apply folder permissions using existing WebSocket connection
     */
    private void applyFolderPermissionsOnActiveSession(UnixGroup group, FolderAccessRequestDTO request) throws IOException {
        // Execute command on existing session
        applyFolderAccessPermissions(request);
        
        log.info("Applied folder permissions for group {} on path {}", 
                group.getGroupName(), request.getFolderPath());
    }

    /**
     * Apply folder permissions using new SSH connection
     */
    private void applyFolderPermissionsOnNewConnection(Asset asset, UnixGroup group, FolderAccessRequestDTO request) throws IOException {
        // Build ACL command based on permissions
        String aclCommand = buildAclCommand(group.getGroupName(), request);
        
        // Execute command on new connection
        String output = sshConnectionService.executeCommandWithCleanOutput(asset, aclCommand);
        
        log.info("Applied folder permissions for group {} on path {}: {}", 
                group.getGroupName(), request.getFolderPath(), output);
    }

    /**
     * Build ACL command based on permissions (unified method)
     */
    private String buildAclCommand(String groupName, FolderAccessRequestDTO request) {
        return buildAclCommandInternal(groupName, request.getReadPermission(), 
                request.getWritePermission(), request.getExecutePermission(), 
                request.getFolderPath(), request.getRecursive());
    }

    /**
     * Internal method to build ACL command - eliminates code duplication
     */
    private String buildAclCommandInternal(String groupName, Boolean readPermission, 
                                         Boolean writePermission, Boolean executePermission, 
                                         String folderPath, Boolean recursive) {
        StringBuilder permissions = new StringBuilder();
        
        if (readPermission != null && readPermission) {
            permissions.append("r");
        }
        if (writePermission != null && writePermission) {
            permissions.append("w");
        }
        if (executePermission != null && executePermission) {
            permissions.append("x");
        }
        
        String permissionString = permissions.toString();
        if (permissionString.isEmpty()) {
            permissionString = "---"; // No permissions
        }
        
        // Build setfacl command
        String command = String.format("sudo setfacl -m g:%s:%s '%s'", groupName, permissionString, folderPath);
        
        if (recursive != null && recursive) {
            command += " -R";
        }
        
        return command;
    }

    /**
     * Remove ACL permissions from the server
     */
    private void removeAclPermissionsFromServer(UnixGroup group) {
        log.info("Removing ACL permissions for group: {} on asset: {}", 
                group.getGroupName(), group.getAsset().getId());

        Asset asset = group.getAsset();

        try {
            for (UnixGroupFolderAccess folderAccess : group.getFolderAccesses()) {
                String command = folderAccess.getRemoveAclCommand(group.getGroupName());
                executeSSHCommand(asset, command);
                log.debug("Removed ACL command: {}", command);
            }
        } catch (Exception e) {
            log.error("Failed to remove ACL permissions for group: {}", group.getGroupName(), e);
            throw new UnixGroupException(FAILED_REMOVE_ACL_MSG + e.getMessage(), e);
        }
    }


    
    /**
     * Get detailed folder suggestions from the server
     */
    public List<UnixFolderSuggestion> getDetailedFolderSuggestions(Long assetId, String path) throws IOException {
        log.debug("Getting detailed folder suggestions for asset: {} and path: {}", assetId, path);
        
        Asset asset = assetService.findById(assetId);
        if (asset == null) {
            throw new IllegalArgumentException("Asset not found: " + assetId);
        }
        
        return getDetailedFolderSuggestions(asset, path);
    }
    

    /**
     * Check if a group is a system group
     */
    private boolean isSystemGroup(String groupName, Integer groupId) {
        // System groups typically have IDs < 1000
        return groupId < 1000 || groupName.startsWith("system-") || groupName.startsWith("sys-");
    }

    /**
     * Perform group synchronization
     */
    private UnixGroupDTO.SyncResult performGroupSync(Asset asset, 
                                                        List<UnixGroupDTO> serverGroups,
                                                        List<UnixGroup> localGroups,
                                                        User user) {
        SyncCounters counters = new SyncCounters();
        List<String> errors = new ArrayList<>();

        // Create a map of local groups by name
        Map<String, UnixGroup> localGroupMap = localGroups.stream()
                .collect(Collectors.toMap(UnixGroup::getGroupName, g -> g));

        // Process server groups
        processServerGroups(asset, serverGroups, localGroupMap, user, counters, errors);

        // Remove groups that no longer exist on server (only custom groups)
        removeObsoleteGroups(localGroups, serverGroups, counters, errors);

        return buildSyncResult(counters, errors);
    }
    
    /**
     * Process server groups for synchronization
     */
    private void processServerGroups(Asset asset, List<UnixGroupDTO> serverGroups, 
                                   Map<String, UnixGroup> localGroupMap, User user,
                                   SyncCounters counters, List<String> errors) {
        for (UnixGroupDTO serverGroup : serverGroups) {
            try {
                UnixGroup localGroup = localGroupMap.get(serverGroup.getGroupName());
                
                if (localGroup == null) {
                    createNewGroup(asset, serverGroup, user);
                    counters.groupsAdded++;
                } else {
                    updateExistingGroup(localGroup, serverGroup);
                    counters.groupsUpdated++;
                }
            } catch (Exception e) {
                errors.add("Failed to sync group " + serverGroup.getGroupName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Create a new group from server data
     */
    private void createNewGroup(Asset asset, UnixGroupDTO serverGroup, User user) {
        UnixGroup newGroup = UnixGroup.builder()
                .asset(asset)
                .groupName(serverGroup.getGroupName())
                .groupId(serverGroup.getGroupId())
                .description(serverGroup.getDescription())
                .isSystemGroup(serverGroup.getIsSystemGroup())
                .createdBy(user)
                .build();
        
        unixGroupRepository.save(newGroup);
    }
    
    /**
     * Update an existing group with server data
     */
    private void updateExistingGroup(UnixGroup localGroup, UnixGroupDTO serverGroup) {
        localGroup.setGroupId(serverGroup.getGroupId());
        localGroup.setDescription(serverGroup.getDescription());
        localGroup.setIsSystemGroup(serverGroup.getIsSystemGroup());
        localGroup.markAsSynced();
        
        unixGroupRepository.save(localGroup);
    }
    
    /**
     * Remove groups that no longer exist on server
     */
    private void removeObsoleteGroups(List<UnixGroup> localGroups, List<UnixGroupDTO> serverGroups,
                                    SyncCounters counters, List<String> errors) {
        for (UnixGroup localGroup : localGroups) {
            if (localGroup.isCustomGroup()) {
                boolean existsOnServer = serverGroups.stream()
                        .anyMatch(sg -> sg.getGroupName().equals(localGroup.getGroupName()));
                
                if (!existsOnServer) {
                    try {
                        unixGroupRepository.delete(localGroup);
                        counters.groupsRemoved++;
                    } catch (Exception e) {
                        errors.add("Failed to remove group " + localGroup.getGroupName() + ": " + e.getMessage());
                    }
                }
            } else {
                counters.groupsSkipped++;
            }
        }
    }
    
    /**
     * Build the final sync result
     */
    private UnixGroupDTO.SyncResult buildSyncResult(SyncCounters counters, List<String> errors) {
        return UnixGroupDTO.SyncResult.builder()
                .groupsAdded(counters.groupsAdded)
                .groupsUpdated(counters.groupsUpdated)
                .groupsRemoved(counters.groupsRemoved)
                .groupsSkipped(counters.groupsSkipped)
                .errors(errors)
                .success(errors.isEmpty())
                .build();
    }
    
    /**
     * Helper class to track sync counters
     */
    private static class SyncCounters {
        int groupsAdded = 0;
        int groupsUpdated = 0;
        int groupsRemoved = 0;
        int groupsSkipped = 0;
    }

    /**
     * Update sync timestamps
     */
    private void updateSyncTimestamps(Long assetId) {
        List<UnixGroup> groups = unixGroupRepository.findByAssetIdOrderByGroupNameAsc(assetId);
        for (UnixGroup group : groups) {
            group.markAsSynced();
        }
        unixGroupRepository.saveAll(groups);
    }

    /**
     * Get Unix group statistics for an asset
     */
    public Map<String, Object> getGroupStatistics(Long assetId) {
        log.debug("Getting Unix group statistics for asset: {}", assetId);
        
        Map<String, Object> statistics = new HashMap<>();
        
        // Total groups
        long totalGroups = unixGroupRepository.countByAssetId(assetId);
        statistics.put("totalGroups", totalGroups);
        
        // Custom groups
        long customGroups = unixGroupRepository.countByAssetIdAndIsSystemGroupFalse(assetId);
        statistics.put("customGroups", customGroups);
        
        // System groups
        long systemGroups = unixGroupRepository.countByAssetIdAndIsSystemGroupTrue(assetId);
        statistics.put("systemGroups", systemGroups);
        
        // Groups with folder access
        List<UnixGroup> groupsWithAccess = unixGroupRepository.findByAssetIdOrderByGroupNameAsc(assetId).stream()
                .filter(group -> !group.getFolderAccesses().isEmpty())
                .toList();
        statistics.put("groupsWithFolderAccess", groupsWithAccess.size());
        
        // Total folder access entries
        long totalFolderAccesses = groupsWithAccess.stream()
                .mapToLong(group -> group.getFolderAccesses().size())
                .sum();
        statistics.put("totalFolderAccesses", totalFolderAccesses);
        
        return statistics;
    }

    /**
     * Get Unix group dashboard statistics
     */
    public Map<String, Object> getDashboardStatistics() {
        log.debug("Getting Unix group dashboard statistics");
        
        Map<String, Object> dashboard = new HashMap<>();
        
        // Total groups across all assets
        long totalGroups = unixGroupRepository.count();
        dashboard.put("totalGroups", totalGroups);
        
        // Total custom groups
        long totalCustomGroups = unixGroupRepository.findAll().stream()
                .mapToLong(group -> group.getIsSystemGroup() ? 0 : 1)
                .sum();
        dashboard.put("totalCustomGroups", totalCustomGroups);
        
        // Total system groups
        long totalSystemGroups = unixGroupRepository.findAll().stream()
                .mapToLong(group -> group.getIsSystemGroup() ? 1 : 0)
                .sum();
        dashboard.put("totalSystemGroups", totalSystemGroups);
        
        // Groups with folder access
        long groupsWithAccess = unixGroupRepository.findAll().stream()
                .mapToLong(group -> group.getFolderAccesses().isEmpty() ? 0 : 1)
                .sum();
        dashboard.put("groupsWithFolderAccess", groupsWithAccess);
        
        // Total folder access entries
        long totalFolderAccesses = unixGroupRepository.findAll().stream()
                .mapToLong(group -> group.getFolderAccesses().size())
                .sum();
        dashboard.put("totalFolderAccesses", totalFolderAccesses);
        
        // Recent groups (created in last 7 days)
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long recentGroups = unixGroupRepository.findAll().stream()
                .mapToLong(group -> group.getCreatedAt().isAfter(weekAgo) ? 1 : 0)
                .sum();
        dashboard.put("recentGroups", recentGroups);
        
        return dashboard;
    }

    // ============================================================================
    // SSH OPERATIONS (from UnixGroupSSHService)
    // ============================================================================

    /**
     * Execute SSH command intelligently - prefer WebSocket reuse, fallback to new connection
     */
    private String executeSSHCommand(Asset asset, String command) throws IOException {
        return executeSSHCommandWithSession(asset, command, null);
    }

    /**
     * Execute SSH command with optional session ID for direct WebSocket reuse
     */
    private String executeSSHCommandWithSession(Asset asset, String command, String sessionId) throws IOException {
        log.debug("Executing SSH command for asset: {} - {} with sessionId: {}", asset.getId(), command, sessionId);
        
        // If sessionId is provided, try to use it directly
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            try {
                log.debug("Using provided WebSocket SSH session: {}", sessionId);
                return executeCommandOnSessionFast(sessionId, command);
            } catch (Exception e) {
                log.debug("Failed to use provided session {}, falling back to lookup: {}", sessionId, e.getMessage());
            }
        }
        
        try {
            // Try to find existing WebSocket SSH connection
            String foundSessionId = terminalService.findActiveSessionForAsset(asset.getId());
            if (foundSessionId != null) {
                log.debug("Using found WebSocket SSH connection for asset: {} with session: {}", asset.getId(), foundSessionId);
                return executeCommandOnSessionFast(foundSessionId, command);
            }
        } catch (Exception e) {
            log.debug("Failed to use WebSocket SSH connection, falling back to new connection: {}", e.getMessage());
        }
        
        // Fallback to creating new SSH connection
        log.debug("Creating new SSH connection for asset: {}", asset.getId());
        return sshConnectionService.executeCommandWithCleanOutput(asset, command);
    }

    /**
     * Execute command on existing WebSocket SSH session with fast response (non-interactive)
     * This method uses JSch ChannelExec for direct command execution without callbacks
     */
    private String executeCommandOnSessionFast(String sessionId, String command) throws IOException {
        log.debug("Executing fast command on session {}: {}", sessionId, command);
        
        try {
            // Try direct JSch ChannelExec approach first (fastest)
            return executeCommandDirectWithJSch(sessionId, command);
        } catch (Exception e) {
            log.debug("Direct JSch execution failed, falling back to TerminalService: {}", e.getMessage());
            // Fallback to the existing working method
            return terminalService.executeCommandOnSession(sessionId, command);
        }
    }

    /**
     * Execute command directly using JSch ChannelExec for immediate output
     * This bypasses the callback mechanism and gets output directly
     */
    private String executeCommandDirectWithJSch(String sessionId, String command) throws IOException {
        log.debug("Executing command directly with JSch ChannelExec: {}", command);
        
        TerminalSession session = validateTerminalSession(sessionId);
        SSHConnectionService.SSHConnection sshConnection = getSSHConnection(session);
        com.jcraft.jsch.Session jschSession = getJSchSessionFromSSHConnection(sshConnection);
        
        return executeCommandWithChannel(jschSession, command);
    }
    
    /**
     * Validate terminal session and return it
     */
    private TerminalSession validateTerminalSession(String sessionId) throws IOException {
        TerminalSession session = terminalService.getSession(sessionId);
        if (session == null) {
            throw new IOException("Terminal session not found: " + sessionId);
        }

        if (!session.isConnected()) {
            throw new IOException("Terminal session not connected: " + sessionId);
        }

        return session;
    }
    
    /**
     * Get SSH connection from terminal session
     */
    private SSHConnectionService.SSHConnection getSSHConnection(TerminalSession session) throws IOException {
        Object sshConnectionObj = session.getSSHConnection();
        if (sshConnectionObj == null) {
            throw new IOException("SSH connection not available in session: " + session.getSessionId());
        }
        
        return (SSHConnectionService.SSHConnection) sshConnectionObj;
    }
    
    /**
     * Execute command using JSch channel
     */
    private String executeCommandWithChannel(com.jcraft.jsch.Session jschSession, String command) throws IOException {
        if (jschSession == null || !jschSession.isConnected()) {
            throw new IOException("JSch session not available or not connected");
        }

        try {
            com.jcraft.jsch.ChannelExec channelExec = (com.jcraft.jsch.ChannelExec) jschSession.openChannel("exec");
            
            try {
                channelExec.setCommand(command);
                java.io.InputStream in = channelExec.getInputStream();
                java.io.InputStream err = channelExec.getErrStream();
                
                channelExec.connect();
                log.debug("Starting to read output for command: {}", command);
                
                CommandOutputReader reader = new CommandOutputReader(in, err);
                String result = reader.readOutput(channelExec);
                
                validateCommandOutput(result, command);
                return result;
                
            } finally {
                channelExec.disconnect();
            }
        } catch (com.jcraft.jsch.JSchException e) {
            throw new IOException("Failed to create or connect JSch channel: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate command output for completeness
     */
    private void validateCommandOutput(String result, String command) throws IOException {
        if (result.length() < 10) {
            log.warn("Direct JSch execution returned suspiciously short output ({} chars), may be incomplete", result.length());
            throw new IOException("Output too short, likely incomplete");
        }
        
        if (command.contains("ls") && result.length() < 20) {
            log.warn("Direct JSch execution returned short output ({} chars), may be incomplete", result.length());
            throw new IOException("Output appears too short for ls command");
        }
    }
    
    /**
     * Helper class to read command output from JSch channels
     */
    private static class CommandOutputReader {
        private final java.io.InputStream in;
        private final java.io.InputStream err;
        private final byte[] buffer = new byte[1024];
        
        public CommandOutputReader(java.io.InputStream in, java.io.InputStream err) {
            this.in = in;
            this.err = err;
        }
        
        public String readOutput(com.jcraft.jsch.ChannelExec channelExec) throws IOException {
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            long startTime = System.currentTimeMillis();
            long timeout = 30000; // 30 second timeout
            
            while (true) {
                boolean hasData = readFromStreams(output, errorOutput);
                
                if (channelExec.isClosed() || isTimeoutExceeded(startTime, timeout)) {
                    if (channelExec.isClosed()) {
                        handleChannelClosed(channelExec, output, errorOutput);
                    } else {
                        log.warn("Command execution timed out after {} ms", timeout);
                    }
                    break;
                }
                
                performAdaptiveDelay(hasData);
            }
            
            String result = output.toString().trim();
            log.debug("Direct JSch execution completed, output length: {}", result.length());
            log.debug("Raw output:\n{}", result);
            
            return result;
        }
        
        private boolean readFromStreams(StringBuilder output, StringBuilder errorOutput) throws IOException {
            boolean hasData = false;
            
            if (in.available() > 0) {
                int bytesRead = in.read(buffer, 0, 1024);
                if (bytesRead > 0) {
                    String chunk = new String(buffer, 0, bytesRead);
                    output.append(chunk);
                    hasData = true;
                    log.debug("Read {} bytes from stdout", bytesRead);
                }
            }
            
            if (err.available() > 0) {
                int bytesRead = err.read(buffer, 0, 1024);
                if (bytesRead > 0) {
                    String chunk = new String(buffer, 0, bytesRead);
                    errorOutput.append(chunk);
                    hasData = true;
                    log.debug("Read {} bytes from stderr", bytesRead);
                }
            }
            
            return hasData;
        }
        
        private void handleChannelClosed(com.jcraft.jsch.ChannelExec channelExec, 
                                       StringBuilder output, StringBuilder errorOutput) throws IOException {
            performFinalRead(output, errorOutput);
            
            int exitStatus = channelExec.getExitStatus();
            log.debug("Command completed with exit status: {}", exitStatus);
            
            if (!errorOutput.isEmpty()) {
                log.warn("Command produced error output: {}", errorOutput.toString());
            }
        }
        
        private void performFinalRead(StringBuilder output, StringBuilder errorOutput) throws IOException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Command execution was interrupted during final read");
                throw new IOException("Command execution was interrupted", e);
            }
            
            // Final read attempt from both streams
            readRemainingData(in, output, "stdout");
            readRemainingData(err, errorOutput, "stderr");
        }
        
        private void readRemainingData(java.io.InputStream stream, StringBuilder output, String streamName) throws IOException {
            while (stream.available() > 0) {
                int bytesRead = stream.read(buffer, 0, 1024);
                if (bytesRead > 0) {
                    String chunk = new String(buffer, 0, bytesRead);
                    output.append(chunk);
                    log.debug("Read final {} bytes from {}", bytesRead, streamName);
                }
            }
        }
        
        private boolean isTimeoutExceeded(long startTime, long timeout) {
            return (System.currentTimeMillis() - startTime) > timeout;
        }
        
        private void performAdaptiveDelay(boolean hasData) throws IOException {
            try {
                Thread.sleep(hasData ? 10 : 50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Command execution was interrupted");
                throw new IOException("Command execution was interrupted", e);
            }
        }
    }

    /**
     * Extract JSch Session from SSHConnection using reflection
     */
    private com.jcraft.jsch.Session getJSchSessionFromSSHConnection(SSHConnectionService.SSHConnection sshConnection) {
        try {
            // Use reflection to access the private session field
            java.lang.reflect.Field sessionField = sshConnection.getClass().getDeclaredField("session");
            sessionField.setAccessible(true);
            return (com.jcraft.jsch.Session) sessionField.get(sshConnection);
        } catch (Exception e) {
            log.debug("Could not extract JSch session via reflection: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse group output from server into basic group information
     */
    private List<UnixGroupInfo> parseGroupOutput(String output) {
        List<UnixGroupInfo> groups = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || isNotGroupData(trimmedLine)) continue;
            
            String[] parts = trimmedLine.split(":");
            if (parts.length >= 2) {
                try {
                    String groupName = parts[0];
                    Integer groupId = Integer.parseInt(parts[1]);
                    String members = parts.length > 2 ? parts[2] : "";
                    
                    groups.add(UnixGroupInfo.builder()
                            .groupName(groupName)
                            .groupId(groupId)
                            .description("")
                            .isSystemGroup(isSystemGroup(groupName, groupId))
                            .members(Arrays.asList(members.split(",")))
                            .build());
                } catch (NumberFormatException e) {
                    log.debug("Skipping malformed group line: {}", trimmedLine);
                }
            }
        }
        
        return groups;
    }

    /**
     * Get all groups from the server (returns UnixGroupInfo for backward compatibility)
     */
    public List<UnixGroupInfo> getGroupsFromServer(Asset asset) throws IOException {
        return getGroupsFromServer(asset, null);
    }

    /**
     * Get all groups from the server with optional session ID
     */
    public List<UnixGroupInfo> getGroupsFromServer(Asset asset, String sessionId) throws IOException {
        log.debug("Fetching groups from server for asset: {} with sessionId: {}", asset.getId(), sessionId);

        // Use UnixCommandBuilder for server-type-aware commands
        String command = UnixCommandBuilder.buildGetGroupsCommand(asset);
        String output = executeSSHCommandWithSession(asset, command, sessionId);
        
        log.debug("Clean groups command output: {}", output);
        
        List<UnixGroupInfo> groups = parseGroupOutput(output);
        
        log.debug("Parsed {} groups from server", groups.size());
        return groups;
    }

    /**
     * Get groups from server (returns UnixGroupDTO for DTO compatibility)
     */
    public List<UnixGroupDTO> getGroupsFromServerAsDTO(Asset asset) throws IOException {
        return getGroupsFromServerAsDTO(asset, null);
    }

    /**
     * Get groups from server with optional session ID (returns UnixGroupDTO for DTO compatibility)
     */
    public List<UnixGroupDTO> getGroupsFromServerAsDTO(Asset asset, String sessionId) throws IOException {
        log.debug("Getting groups from server for asset: {} with sessionId: {}", asset.getId(), sessionId);

        // Use UnixCommandBuilder for server-type-aware commands
        String command = UnixCommandBuilder.buildGetGroupsCommand(asset);
        String output = executeSSHCommandWithSession(asset, command, sessionId);
        
        log.debug("Clean groups command output: {}", output);
        
        List<UnixGroupInfo> parsedGroups = parseGroupOutput(output);
        List<UnixGroupDTO> groups = new ArrayList<>();
        
        for (UnixGroupInfo groupInfo : parsedGroups) {
            groups.add(UnixGroupDTO.forSync(
                    groupInfo.getGroupName(),
                    groupInfo.getGroupId(),
                    "",
                    groupInfo.getIsSystemGroup(),
                    groupInfo.getMembers()
            ));
        }
        
        log.debug("Parsed {} groups from server", groups.size());
        return groups;
    }
    
    /**
     * Check if a line is clearly not group data (connection messages, prompts, etc.)
     */
    private boolean isNotGroupData(String line) {
        String lowerLine = line.toLowerCase();
        return lowerLine.contains("linux") ||
               lowerLine.contains("gnu/linux") ||
               lowerLine.contains("last login") ||
               lowerLine.contains("root@") ||
               lowerLine.contains("the programs included") ||
               lowerLine.contains("exact distribution terms") ||
               lowerLine.contains("individual files") ||
               lowerLine.contains("absolutely no warranty") ||
               lowerLine.contains("permitted by applicable law") ||
               lowerLine.contains("~#") ||
               lowerLine.contains("~$") ||
               lowerLine.contains("getent ") ||
               lowerLine.contains("cut ") ||
               lowerLine.contains("sort") ||
               line.startsWith("[?") || // ANSI control sequences
               line.contains("2004h") || // xterm control sequences
               line.contains("2004l");
    }

    /**
     * Get folder suggestions from the server
     */
    public List<String> getFolderSuggestions(Asset asset, String path) throws IOException {
        log.debug("Getting folder suggestions for asset: {} and path: {}", asset.getId(), path);

        // Use UnixCommandBuilder for server-type-aware commands
        String command = UnixCommandBuilder.buildFolderFindCommand(asset, path);
        String output = executeSSHCommand(asset, command);
        
        log.debug("Clean command output: {}", output);
        
        // Parse the clean output into folder list
        List<String> folders = parseFolderOutput(output);
        
        log.debug("Parsed folder suggestions: {}", folders);
        return folders;
    }

    /**
     * Simple method to test if missing folders/links are due to permission issues
     * Compares basic ls vs sudo ls to identify permission problems
     */
    public String debugFolderPermissions(Asset asset, String path) throws IOException {
        log.info("Debugging folder permissions for path: {}", path);
        
        StringBuilder results = new StringBuilder();
        
        // Test 1: Basic ls command
        try {
            String basicCommand = String.format("ls -la '%s' 2>/dev/null | head -50", path);
            String basicResult = executeSSHCommand(asset, basicCommand);
            results.append("=== Basic ls -la ===\n");
            results.append(basicResult).append("\n\n");
            
            String[] basicLines = basicResult.split("\n");
            results.append("Basic ls line count: ").append(basicLines.length).append("\n\n");
            
        } catch (Exception e) {
            results.append("Basic ls ERROR: ").append(e.getMessage()).append("\n\n");
        }
        
        // Test 2: Sudo ls command (if available)
        try {
            String sudoCommand = String.format("sudo ls -la '%s' 2>/dev/null | head -50", path);
            String sudoResult = executeSSHCommand(asset, sudoCommand);
            results.append("=== Sudo ls -la ===\n");
            results.append(sudoResult).append("\n\n");
            
            String[] sudoLines = sudoResult.split("\n");
            results.append("Sudo ls line count: ").append(sudoLines.length).append("\n\n");
            
        } catch (Exception e) {
            results.append("Sudo ls ERROR: ").append(e.getMessage()).append("\n\n");
        }
        
        // Test 3: Current user
        try {
            String whoamiResult = executeSSHCommand(asset, "whoami");
            results.append("Current user: ").append(whoamiResult).append("\n\n");
        } catch (Exception e) {
            results.append("Whoami ERROR: ").append(e.getMessage()).append("\n\n");
        }
        
        return results.toString();
    }
    
    /**
     * Get detailed folder suggestions from the server with file information
     */
    public List<UnixFolderSuggestion> getDetailedFolderSuggestions(Asset asset, String path) throws IOException {
        return getDetailedFolderSuggestions(asset, path, null);
    }

    /**
     * Get detailed folder suggestions from the server with file information
     * @param asset The asset to connect to
     * @param path The path to get suggestions for
     * @param sessionId Optional terminal session ID to reuse existing WebSocket SSH connection
     */
    public List<UnixFolderSuggestion> getDetailedFolderSuggestions(Asset asset, String path, String sessionId) throws IOException {
        log.debug("Getting detailed folder suggestions for asset: {} and path: {} with sessionId: {}", asset.getId(), path, sessionId);

        // Check if this is a partial path that needs special handling
        if (isPartialPath(path)) {
            return getPartialPathSuggestions(asset, path, sessionId);
        } else {
            // Use normal detailed ls command for complete paths
            String command = UnixCommandBuilder.buildDetailedFolderListCommand(asset, path);
            String output = executeSSHCommandWithSession(asset, command, sessionId);
            
            log.debug("Clean command output: {}", output);
            
            // Parse the detailed output into folder suggestions
            List<UnixFolderSuggestion> suggestions = parseDetailedFolderOutput(output, path);
            
            log.debug("Parsed detailed folder suggestions: {}", suggestions);
            return suggestions;
        }
    }

    /**
     * Get folder suggestions for partial paths (e.g., '/usr/g' -> folders starting with 'g' in /usr)
     */
    private List<UnixFolderSuggestion> getPartialPathSuggestions(Asset asset, String partialPath, String sessionId) throws IOException {
        log.debug("Getting partial path suggestions for: {}", partialPath);

        // Use the new partial path command
        String command = UnixCommandBuilder.buildPartialPathSuggestionCommand(asset, partialPath);
        String output = executeSSHCommandWithSession(asset, command, sessionId);
        
        log.debug("Partial path command output: {}", output);
        
        // Parse the output into folder suggestions
        List<UnixFolderSuggestion> suggestions = parsePartialPathOutput(output);
        
        log.debug("Parsed {} partial path suggestions", suggestions.size());
        return suggestions;
    }

    /**
     * Check if the given path is a partial path that needs special handling
     */
    private boolean isPartialPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // If path doesn't end with '/' and the last segment might be incomplete
        // Examples: '/usr/g', '/et', '/var/log/a'
        if (!path.endsWith("/")) {
            // Get the last segment
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                String lastSegment = path.substring(lastSlash + 1);
                // If the last segment is not empty and relatively short, treat as partial
                return !lastSegment.isEmpty() && lastSegment.length() <= 10;
            }
        }
        
        return false;
    }

    /**
     * Parse output from partial path commands into UnixFolderSuggestion objects
     * Now handles ls -la output format using the existing parseLsLine method
     */
    private List<UnixFolderSuggestion> parsePartialPathOutput(String output) {
        List<UnixFolderSuggestion> suggestions = new ArrayList<>();
        
        if (output == null || output.trim().isEmpty()) {
            log.debug("Empty output for partial path suggestions");
            return suggestions;
        }
        
        log.debug("Parsing partial path output with {} characters", output.length());
        log.debug("Raw partial path output:\n{}", output);
        
        String[] lines = output.split("\n");
        log.debug("Split into {} lines", lines.length);
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            log.debug("Processing partial path line {}: '{}'", i, trimmedLine);
            
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("total ")) {
                continue;
            }
            // Use the existing parseLsLine method to handle ls -la format
            // For partial paths, we pass empty string as basePath since the full path is in the ls output
            UnixFolderSuggestion suggestion = parseLsLine(trimmedLine, "");
            
            if (suggestion != null) {
                suggestions.add(suggestion);
                log.debug("Added partial path suggestion: {}", suggestion.getPath());
            } else {
                log.debug("Failed to parse partial path line {}: '{}'", i, trimmedLine);
            }
        }
        
        log.debug("Parsed {} partial path suggestions from {} lines", suggestions.size(), lines.length);
        return suggestions;
    }

    /**
     * Parse a single ls -la line into UnixFolderSuggestion
     * Format: -rw-r--r-- 1 owner group 1234 Dec 25 10:30 filename
     * Handles various date formats: "Sep 16 04:33", "Jan 30  2023", "Dec 30  2024"
     */
    private UnixFolderSuggestion parseLsLine(String line, String basePath) {
        try {
            log.debug("Parsing ls line: {} with basePath: {}", line, basePath);
            
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                return null;
            }
            
            String[] parts = trimmedLine.split("\\s+");
            if (parts.length < 8) {
                log.debug("Skipping malformed ls line (too few parts: {}): {}", parts.length, line);
                return null;
            }
            
            LsLineData lineData = extractLineData(parts);
            if (lineData == null) {
                return null;
            }
            
            Long size = parseSize(lineData.sizeStr);
            String fullPath = buildFullPath(basePath, lineData.name);
            String lastModified = String.format("%s %s %s", lineData.dateInfo.month, lineData.dateInfo.day, lineData.dateInfo.timeOrYear).trim();
            
            return UnixFolderSuggestion.builder()
                    .path(fullPath)
                    .type(lineData.type)
                    .owner(lineData.owner)
                    .group(lineData.group)
                    .size(size)
                    .permissions(lineData.permissions)
                    .lastModified(lastModified)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error parsing ls line: {}", line, e);
            return null;
        }
    }
    
    /**
     * Extract data from ls line parts
     */
    private LsLineData extractLineData(String[] parts) {
        String permissions = parts[0];
        String owner = parts[2];
        String group = parts[3];
        String sizeStr = parts[4];
        String month = parts[5];
        String day = parts[6];
        String timeOrYear = parts[7];
        
        String name = extractFileName(parts);
        if (name == null) {
            return null; // Skipped . or .. entry
        }
        
        String type = determineFileType(permissions);
        
        DateInfo dateInfo = new DateInfo(month, day, timeOrYear);
        return new LsLineData(permissions, owner, group, sizeStr, dateInfo, name, type);
    }
    
    /**
     * Extract filename from parts array
     */
    private String extractFileName(String[] parts) {
        String name;
        if (parts.length == 8) {
            name = parts[7];
        } else if (parts.length == 9) {
            name = parts[8];
        } else {
            // Complex case: filename might have spaces
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 8; i < parts.length; i++) {
                if (i > 8) nameBuilder.append(" ");
                nameBuilder.append(parts[i]);
            }
            name = nameBuilder.toString();
        }
        
        // Skip . and .. entries
        if (".".equals(name) || "..".equals(name)) {
            log.debug("Skipping . or .. entry");
            return null;
        }
        
        return name;
    }
    
    /**
     * Determine file type from permissions
     */
    private String determineFileType(String permissions) {
        if (permissions.startsWith("d")) {
            return "directory";
        } else if (permissions.startsWith("l")) {
            return "link";
        } else {
            return "file";
        }
    }
    
    /**
     * Parse size from string
     */
    private Long parseSize(String sizeStr) {
        try {
            return Long.parseLong(sizeStr);
        } catch (NumberFormatException e) {
            log.debug("Could not parse size: {}", sizeStr);
            return null;
        }
    }
    
    /**
     * Build full path from base path and name
     */
    private String buildFullPath(String basePath, String name) {
        if (basePath.equals(ROOT_PATH)) {
            return ROOT_PATH + name;
        } else if (basePath.endsWith("/")) {
            return basePath + name;
        } else if (basePath.isEmpty()) {
            return name;
        } else {
            return basePath + PATH_SEPARATOR + name;
        }
    }
    
    /**
     * Helper class to hold date information
     */
    private static class DateInfo {
        final String month;
        final String day;
        final String timeOrYear;
        
        DateInfo(String month, String day, String timeOrYear) {
            this.month = month;
            this.day = day;
            this.timeOrYear = timeOrYear;
        }
    }

    /**
     * Helper class to hold ls line data
     */
    private static class LsLineData {
        final String permissions;
        final String owner;
        final String group;
        final String sizeStr;
        final DateInfo dateInfo;
        final String name;
        final String type;
        
        LsLineData(String permissions, String owner, String group, String sizeStr, 
                  DateInfo dateInfo, String name, String type) {
            this.permissions = permissions;
            this.owner = owner;
            this.group = group;
            this.sizeStr = sizeStr;
            this.dateInfo = dateInfo;
            this.name = name;
            this.type = type;
        }
    }


    
    /**
     * Parse folder output into list of folder paths
     */
    private List<String> parseFolderOutput(String output) {
        List<String> folders = new ArrayList<>();
        
        if (output == null || output.trim().isEmpty()) {
            return folders;
        }
        
        String[] lines = output.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // Use helper method to check if line should be skipped
            if (shouldSkipOutputLine(trimmedLine, i)) continue;
            
            // Only add valid folder paths (must start with /)
            if (trimmedLine.startsWith("/")) {
                folders.add(trimmedLine);
            }
        }
        
        return folders;
    }
    
    /**
     * Parse detailed ls -la output into UnixFolderSuggestion objects
     */
    private List<UnixFolderSuggestion> parseDetailedFolderOutput(String output, String basePath) {
        List<UnixFolderSuggestion> suggestions = new ArrayList<>();
        
        if (output == null || output.trim().isEmpty()) {
            log.debug("Empty output for folder suggestions, basePath: {}", basePath);
            return suggestions;
        }
        
        log.debug("Parsing folder output for basePath: {}, output length: {}", basePath, output.length());
        log.debug("Raw output:\n{}", output);
        
        String[] lines = output.split("\n");
        log.debug("Split into {} lines", lines.length);
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            log.debug("Processing line {}: '{}'", i, trimmedLine);
            
            // Use helper method to check if line should be skipped
            if (shouldSkipOutputLine(trimmedLine, i)) {
                continue;
            }
            
            // Parse ls -la format: permissions links owner group size date time name
            UnixFolderSuggestion suggestion = parseLsLine(trimmedLine, basePath);
            if (suggestion != null) {
                suggestions.add(suggestion);
                log.debug("Added suggestion: {}", suggestion.getPath());
            } else {
                log.debug("Failed to parse line {}: '{}'", i, trimmedLine);
            }
        }
        
        log.debug("Parsed {} suggestions from {} lines", suggestions.size(), lines.length);
        return suggestions;
    }

    /**
     * Helper method to check if a line should be skipped during output parsing
     * Eliminates duplication between different parsing methods
     */
    private boolean shouldSkipOutputLine(String trimmedLine, int lineIndex) {
        // Skip empty lines
        if (trimmedLine.isEmpty()) {
            log.debug("Skipping empty line {}", lineIndex);
            return true;
        }
        
        // Skip total line (e.g., "total 1234")
        if (trimmedLine.startsWith("total ")) {
            log.debug("Skipping total line: {}", trimmedLine);
            return true;
        }
        
        return false;
    }
    
    /**
     * Test method to verify parsing with sample data (for debugging)
     */
    public List<UnixFolderSuggestion> testParsing(String sampleOutput, String basePath) {
        log.info("Testing parsing with sample output for basePath: {}", basePath);
        return parseDetailedFolderOutput(sampleOutput, basePath);
    }

    /**
     * Create a group on the server
     */
    public void createGroupOnServer(Asset asset, String groupName, String description) throws IOException {
        createGroupOnServer(asset, groupName, description, null);
    }

    /**
     * Create a group on the server with optional session ID
     */
    public void createGroupOnServer(Asset asset, String groupName, String description, String sessionId) throws IOException {
        log.info("Creating group on server: {} for asset: {} with sessionId: {}", groupName, asset.getId(), sessionId);

        // Find or use provided session ID to reuse it for both commands
        String usedSessionId = findOrUseSessionId(asset, sessionId);

        // Create reusable SSH connection if no session exists
        SSHConnectionService.SSHConnection reusableConnection = null;
        if (usedSessionId == null) {
            reusableConnection = createReusableSSHConnection(asset);
        }

        try {
            String command = String.format("sudo groupadd %s", groupName);
            if (reusableConnection != null) {
                executeCommandOnConnection(reusableConnection, command);
            } else {
                executeSSHCommandWithSession(asset, command, usedSessionId);
            }
            
            // Note: groupmod doesn't support -c option for comments/descriptions on Linux
            // Group descriptions are typically stored in /etc/group but can't be set via groupmod
            // If description is needed, it would require manual editing of /etc/group
            // For now, we skip setting the description to avoid errors and timeouts
            if (description != null && !description.trim().isEmpty()) {
                log.debug("Group description provided but not set (groupmod doesn't support -c option): {}", description);
            }
        } finally {
            // Always disconnect reusable connection if we created one
            if (reusableConnection != null) {
                try {
                    reusableConnection.disconnect();
                    log.debug("Disconnected reusable SSH connection for asset: {}", asset.getId());
                } catch (Exception e) {
                    log.warn("Error disconnecting reusable SSH connection: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Find existing session or use provided session ID to reuse for multiple commands
     */
    private String findOrUseSessionId(Asset asset, String providedSessionId) {
        // If session ID is provided, use it
        if (providedSessionId != null && !providedSessionId.trim().isEmpty()) {
            return providedSessionId;
        }
        
        // Try to find existing session for this asset
        try {
            String foundSessionId = terminalService.findActiveSessionForAsset(asset.getId());
            if (foundSessionId != null) {
                log.debug("Found existing session {} for asset {}, will reuse for multiple commands", 
                        foundSessionId, asset.getId());
                return foundSessionId;
            }
        } catch (Exception e) {
            log.debug("Could not find existing session for asset {}: {}", asset.getId(), e.getMessage());
        }
        
        // No session found, return null - we'll create a reusable connection
        return null;
    }

    /**
     * Create a reusable SSH connection for multiple commands
     */
    private SSHConnectionService.SSHConnection createReusableSSHConnection(Asset asset) throws IOException {
        try {
            log.debug("Creating reusable SSH connection for asset: {}", asset.getId());
            
            // Get current user and credentials
            User currentUser = userService.getCurrentUser();
            AssetCredential sshCredential = assetService.getSSHCredentialsForAsset(asset.getId(), currentUser);
            if (sshCredential == null) {
                throw new IOException("No SSH credentials found for asset: " + asset.getId());
            }
            
            String userKey = keycloakService.getUserKey();
            if (userKey == null || userKey.isEmpty()) {
                throw new IOException("User encryption key not available");
            }
            
            // Create SSH connection
            SSHConnectionService.SSHConnection connection = sshConnectionService.createSSHConnection(
                    asset.getHostAddress(),
                    asset.getPortNumber() != null ? Integer.parseInt(asset.getPortNumber()) : 22,
                    sshCredential,
                    userKey
            );
            
            log.debug("Created reusable SSH connection for asset: {}", asset.getId());
            return connection;
        } catch (Exception e) {
            log.error("Failed to create reusable SSH connection for asset {}: {}", asset.getId(), e.getMessage());
            throw new IOException("Failed to create SSH connection: " + e.getMessage(), e);
        }
    }

    /**
     * Execute command on a reusable SSH connection
     */
    private String executeCommandOnConnection(SSHConnectionService.SSHConnection connection, String command) throws IOException {
        StringBuilder output = new StringBuilder();
        final Object outputLock = new Object(); // Dedicated lock object for synchronization
        int timeoutMs = Constants.SSH_COMMAND_TIMEOUT_MS;
        int checkIntervalMs = 100;

        // Clear previous output and start output reader
        connection.startOutputReader(data -> {
            synchronized (outputLock) {
                output.append(data);
            }
        });

        try {
            // Wait for prompt (connection ready)
            Thread.sleep(500);
            
            // Send command
            connection.sendInput(command + "\n");
            
            // Wait for command completion using shared helper method
            boolean commandCompleted = sshConnectionService.waitForCommandCompletion(output, outputLock, command, timeoutMs, checkIntervalMs);
            
            if (!commandCompleted) {
                log.warn("Command execution timed out after {}ms", timeoutMs);
            }
            
            // Extract command output (remove command and prompt)
            synchronized (outputLock) {
                String fullOutput = output.toString();
                // Extract only the command result (between command and prompt)
                String result = fullOutput.replaceAll(".*" + Pattern.quote(command) + "\\s*\n?", "");
                result = result.replaceAll("[$#].*$", "").trim();
                return result;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command execution interrupted", e);
        } catch (Exception e) {
            log.error("Failed to execute command on reusable connection: {}", command, e);
            throw new IOException("Failed to execute command: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a group exists on the server
     */
    public boolean doesGroupExistOnServer(Asset asset, String groupName) throws IOException {
        return doesGroupExistOnServer(asset, groupName, null);
    }

    /**
     * Check if a group exists on the server with optional session ID
     */
    public boolean doesGroupExistOnServer(Asset asset, String groupName, String sessionId) throws IOException {
        log.debug("Checking if group exists on server: {} for asset: {} with sessionId: {}", groupName, asset.getId(), sessionId);

        // Use getent command to check if group exists
        String command = String.format("getent group %s", groupName);
        
        try {
            String output;
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                output = executeSSHCommandWithSession(asset, command, sessionId);
            } else {
                output = executeSSHCommand(asset, command);
            }
            
            // If getent returns output, the group exists
            boolean exists = output != null && !output.trim().isEmpty() && !output.contains("No such group");
            log.debug("Group {} exists on server: {}", groupName, exists);
            return exists;
            
        } catch (IOException e) {
            // If getent fails, assume group doesn't exist
            log.debug("Failed to check if group {} exists, assuming it doesn't: {}", groupName, e.getMessage());
            return false;
        }
    }

    /**
     * Delete a group from the server
     */
    public void deleteGroupFromServer(Asset asset, String groupName) throws IOException {
        log.info("Deleting group from server: {} for asset: {}", groupName, asset.getId());

        String command = String.format("sudo groupdel %s", groupName);
        executeSSHCommand(asset, command);
    }

    /**
     * Apply ACL permissions for a group
     */
    public void applyAclPermissions(Asset asset, UnixGroup group) throws IOException {
        log.info("Applying ACL permissions for group: {} on asset: {}", 
                group.getGroupName(), asset.getId());

        for (UnixGroupFolderAccess folderAccess : group.getFolderAccesses()) {
            String command = folderAccess.getAclCommand(group.getGroupName());
            executeSSHCommand(asset, command);
            log.debug(APPLIED_ACL_COMMAND_MSG, command);
        }
    }

    /**
     * Remove ACL permissions for a group
     */
    public void removeAclPermissions(Asset asset, UnixGroup group) throws IOException {
        log.info("Removing ACL permissions for group: {} on asset: {}", 
                group.getGroupName(), asset.getId());

        for (UnixGroupFolderAccess folderAccess : group.getFolderAccesses()) {
            String command = folderAccess.getRemoveAclCommand(group.getGroupName());
            executeSSHCommand(asset, command);
            log.debug("Removed ACL command: {}", command);
        }
    }

    /**
     * Check if ACL is supported on the server
     */
    public boolean isAclSupported(Asset asset) throws IOException {
        log.debug("Checking ACL support for asset: {}", asset.getId());

        try {
            // Use UnixCommandBuilder for server-type-aware commands
            String command = UnixCommandBuilder.buildAclCheckCommand(asset);
            String output = executeSSHCommand(asset, command);
            return !output.contains("not_supported");
        } catch (IOException e) {
            log.debug("ACL not supported on asset: {}", asset.getId());
            return false;
        }
    }

    /**
     * Install ACL support on the server
     */
    public void installAclSupport(Asset asset) throws IOException {
        log.info("Installing ACL support for asset: {}", asset.getId());

        // Try different package managers based on the OS
        String[] commands = {
            "yum install -y acl",           // CentOS/RHEL/Rocky
            "apt-get update && apt-get install -y acl", // Ubuntu/Debian
            "dnf install -y acl",           // Fedora
            "zypper install -y acl"         // openSUSE
        };

        for (String command : commands) {
            try {
                executeSSHCommand(asset, command);
                log.info("Successfully installed ACL support for asset: {}", asset.getId());
                return;
            } catch (IOException e) {
                log.debug("Failed to install ACL with command: {}", command);
            }
        }

        throw new IOException("Failed to install ACL support on any supported package manager");
    }

    /**
     * Get current ACL permissions for a folder
     */
    public List<AclPermission> getAclPermissions(Asset asset, String folderPath) throws IOException {
        log.debug("Getting ACL permissions for folder: {} on asset: {}", folderPath, asset.getId());

        String command = String.format("sudo getfacl '%s' 2>/dev/null", folderPath);
        String output = executeSSHCommand(asset, command);
        
        List<AclPermission> permissions = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            
            // Parse ACL entries like "group:groupname:rwx"
            Pattern pattern = Pattern.compile("^(group|user):([^:]+):([rwx-]+)$");
            Matcher matcher = pattern.matcher(line.trim());
            
            if (matcher.matches()) {
                String type = matcher.group(1);
                String name = matcher.group(2);
                String perms = matcher.group(3);
                
                permissions.add(AclPermission.builder()
                        .type(type)
                        .name(name)
                        .permissions(perms)
                        .build());
            }
        }
        
        return permissions;
    }

    /**
     * Check if a folder exists on the server
     */
    public boolean folderExists(Asset asset, String folderPath) throws IOException {
        log.debug("Checking if folder exists: {} on asset: {}", folderPath, asset.getId());

        String command = String.format("test -d '%s' && echo 'exists' || echo 'not_exists'", folderPath);
        String output = executeSSHCommand(asset, command);
        
        return output.trim().equals("exists");
    }

    /**
     * Create a folder on the server
     */
    public void createFolder(Asset asset, String folderPath) throws IOException {
        log.info("Creating folder: {} on asset: {}", folderPath, asset.getId());

        String command = String.format("sudo mkdir -p '%s'", folderPath);
        executeSSHCommand(asset, command);
    }

    // ============================================================================
    // VALIDATION METHODS (from UnixGroupValidationService)
    // ============================================================================

    /**
     * Validate group name
     */
    public List<String> validateGroupName(String groupName) {
        List<String> errors = new ArrayList<>();

        if (groupName == null || groupName.trim().isEmpty()) {
            errors.add("Group name is required");
            return errors;
        }

        groupName = groupName.trim();

        if (groupName.length() > MAX_GROUP_NAME_LENGTH) {
            errors.add("Group name cannot exceed " + MAX_GROUP_NAME_LENGTH + " characters");
        }

        if (!GROUP_NAME_PATTERN.matcher(groupName).matches()) {
            errors.add("Group name can only contain letters, numbers, dots, underscores, and hyphens");
        }

        if (groupName.startsWith("-")) {
            errors.add("Group name cannot start with a hyphen");
        }

        if (groupName.startsWith(".") || groupName.endsWith(".")) {
            errors.add("Group name cannot start or end with a dot");
        }

        // Check for reserved names
        if (isReservedGroupName(groupName)) {
            errors.add("Group name is reserved and cannot be used");
        }

        return errors;
    }

    /**
     * Validate folder path
     */
    public List<String> validateFolderPath(String folderPath) {
        List<String> errors = new ArrayList<>();

        if (folderPath == null || folderPath.trim().isEmpty()) {
            errors.add("Folder path is required");
            return errors;
        }

        folderPath = folderPath.trim();

        if (folderPath.length() > MAX_FOLDER_PATH_LENGTH) {
            errors.add("Folder path cannot exceed " + MAX_FOLDER_PATH_LENGTH + " characters");
        }

        if (!folderPath.startsWith("/")) {
            errors.add("Folder path must be an absolute path (start with /)");
        }

        if (!FOLDER_PATH_PATTERN.matcher(folderPath).matches()) {
            errors.add("Folder path contains invalid characters");
        }

        if (folderPath.contains("..")) {
            errors.add("Folder path cannot contain '..' (parent directory references)");
        }

        if (folderPath.contains("//")) {
            errors.add("Folder path cannot contain consecutive slashes");
        }

        // Check for reserved paths
        if (isReservedPath(folderPath)) {
            errors.add("Folder path is reserved and cannot be used");
        }

        return errors;
    }

    /**
     * Validate group creation
     */
    public List<String> validateGroupCreation(String groupName, Long assetId) {
        List<String> errors = new ArrayList<>();

        // Validate group name
        errors.addAll(validateGroupName(groupName));

        // Check if group already exists
        if (unixGroupRepository.existsByGroupNameAndAssetId(groupName, assetId)) {
            errors.add("Group already exists: " + groupName);
        }

        return errors;
    }

    /**
     * Validate folder access
     */
    public List<String> validateFolderAccess(UnixGroupFolderAccess.AccessType accessType, String folderPath) {
        List<String> errors = new ArrayList<>();

        if (accessType == null) {
            errors.add("Access type is required");
        }

        errors.addAll(validateFolderPath(folderPath));

        return errors;
    }

    /**
     * Validate group update
     */
    public List<String> validateGroupUpdate(Long groupId, String newGroupName) {
        List<String> errors = new ArrayList<>();

        // Validate group name
        errors.addAll(validateGroupName(newGroupName));

        // Check if group exists
        UnixGroup existingGroup = unixGroupRepository.findById(groupId).orElse(null);
        if (existingGroup == null) {
            errors.add(GROUP_NOT_FOUND_MSG + groupId);
            return errors;
        }

        // Check if new name conflicts with existing group (excluding current group)
        if (!existingGroup.getGroupName().equals(newGroupName) && 
            unixGroupRepository.existsByGroupNameAndAssetId(newGroupName, existingGroup.getAsset().getId())) {
            errors.add("Group name already exists: " + newGroupName);
        }

        return errors;
    }

    /**
     * Check if group name is reserved
     */
    private boolean isReservedGroupName(String groupName) {
        String[] reservedNames = {
            "root", "daemon", "bin", "sys", "adm", "tty", "disk", "lp", "mail", "news", "uucp",
            "man", "proxy", "kmem", "dialout", "fax", "voice", "cdrom", "floppy", "tape", "sudo",
            "audio", "dip", "www-data", "backup", "operator", "list", "irc", "src", "gnats",
            "shadow", "utmp", "video", "sasl", "plugdev", "staff", "games", "users", "nogroup",
            "system", "admin", "wheel", "nogroup", "nobody", "www", "ftp", "mail", "sshd",
            "postgres", "mysql", "oracle", "apache", "nginx", "redis", "mongodb", "docker"
        };

        for (String reserved : reservedNames) {
            if (groupName.equalsIgnoreCase(reserved)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if path is reserved
     */
    private boolean isReservedPath(String folderPath) {
        String[] reservedPaths = {
            "/", "/bin", "/sbin", "/usr", "/usr/bin", "/usr/sbin", "/etc", "/var", "/var/log",
            "/var/run", "/var/tmp", "/tmp", "/root", "/home", "/opt", "/proc", "/sys", "/dev",
            "/boot", "/lib", "/lib64", "/media", "/mnt", "/run", "/srv", "/lost+found"
        };

        for (String reserved : reservedPaths) {
            if (folderPath.equals(reserved) || folderPath.startsWith(reserved + "/")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate asset compatibility
     */
    public List<String> validateAssetCompatibility(Asset asset) {
        List<String> errors = new ArrayList<>();

        if (asset == null) {
            errors.add("Asset is required");
            return errors;
        }

        // Check if asset is a Unix/Linux server
        if (!isUnixServer(asset)) {
            errors.add("Asset must be a Unix/Linux server");
        }

        return errors;
    }

    /**
     * Check if asset is a Unix server
     */
    private boolean isUnixServer(Asset asset) {
        // Check if asset type is UNIX_SERVER
        return asset.getType() == com.verlake.dam.enums.AssetType.UNIX_SERVER;
    }

    /**
     * Get validation summary
     */
    public String getValidationSummary(List<String> errors) {
        if (errors.isEmpty()) {
            return "Validation passed";
        }

        return "Validation failed: " + String.join(", ", errors);
    }

}
