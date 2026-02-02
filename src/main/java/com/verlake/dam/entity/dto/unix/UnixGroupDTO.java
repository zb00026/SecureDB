package com.verlake.dam.entity.dto.unix;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.unix.UnixGroup;
import com.verlake.dam.entity.unix.UnixGroupFolderAccess;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unified DTO for Unix group operations (create, read, update, sync)
 * Replaces CreateUnixGroupDTO, UnixGroupSyncDTO, and the old UnixGroupDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class UnixGroupDTO {

    // === Basic Group Information ===
    private Long id;
    
    @NotNull(message = "Asset ID is required", groups = {CreateGroup.class, UpdateGroup.class})
    private Long assetId;
    
    private AssetDTO asset;
    
    @NotBlank(message = "Group name is required", groups = {CreateGroup.class, UpdateGroup.class})
    @Size(min = 1, max = 255, message = "Group name must be between 1 and 255 characters", groups = {CreateGroup.class, UpdateGroup.class})
    private String groupName;
    
    private Integer groupId;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters", groups = {CreateGroup.class, UpdateGroup.class})
    private String description;
    
    @Builder.Default
    private Boolean isSystemGroup = false;
    
    // === Metadata ===
    private UserDTO createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSyncedAt;
    
    // === Folder Access Configuration ===
    private List<FolderAccessDTO> folderAccesses;
    
    // === Sync Information ===
    private List<String> members;
    private SyncResult syncResult;
    
    // === Validation Groups ===
    public interface CreateGroup {}
    public interface UpdateGroup {}
    public interface SyncGroup {}

    /**
     * Convert entity to DTO
     */
    public static UnixGroupDTO fromEntity(UnixGroup unixGroup) {
        if (unixGroup == null) {
            return null;
        }

        return UnixGroupDTO.builder()
                .id(unixGroup.getId())
                .assetId(unixGroup.getAsset() != null ? unixGroup.getAsset().getId() : null)
                .asset(AssetDTO.fromEntity(unixGroup.getAsset()))
                .groupName(unixGroup.getGroupName())
                .groupId(unixGroup.getGroupId())
                .description(unixGroup.getDescription())
                .isSystemGroup(unixGroup.getIsSystemGroup())
                .createdBy(convertUserToDTO(unixGroup.getCreatedBy()))
                .createdAt(unixGroup.getCreatedAt())
                .updatedAt(unixGroup.getUpdatedAt())
                .lastSyncedAt(unixGroup.getLastSyncedAt())
                .folderAccesses(unixGroup.getFolderAccesses() != null ? 
                    unixGroup.getFolderAccesses().stream()
                        .map(FolderAccessDTO::fromEntity)
                        .collect(Collectors.toList()) : null)
                .build();
    }

    /**
     * Convert User entity to UserDTO
     */
    private static UserDTO convertUserToDTO(User user) {
        if (user == null) {
            return null;
        }
        
        UserDTO userDTO = new UserDTO();
        userDTO.setUser(user);
        return userDTO;
    }

    /**
     * Create a new UnixGroupDTO for creation
     */
    public static UnixGroupDTO forCreate(Long assetId, String groupName, String description, 
                                       Boolean isSystemGroup, List<FolderAccessDTO> folderAccesses) {
        return UnixGroupDTO.builder()
                .assetId(assetId)
                .groupName(groupName)
                .description(description)
                .isSystemGroup(isSystemGroup != null && isSystemGroup)
                .folderAccesses(folderAccesses)
                .build();
    }

    /**
     * Create a UnixGroupDTO for sync operations
     */
    public static UnixGroupDTO forSync(String groupName, Integer groupId, String description, 
                                     Boolean isSystemGroup, List<String> members) {
        return UnixGroupDTO.builder()
                .groupName(groupName)
                .groupId(groupId)
                .description(description)
                .isSystemGroup(isSystemGroup != null && isSystemGroup)
                .members(members)
                .build();
    }

    /**
     * Create a UnixGroupDTO for update operations
     */
    public static UnixGroupDTO forUpdate(String description, List<FolderAccessDTO> folderAccesses) {
        return UnixGroupDTO.builder()
                .description(description)
                .folderAccesses(folderAccesses)
                .build();
    }

    /**
     * DTO for folder access configuration
     * Supports both frontend format (individual permissions) and backend format (accessType)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FolderAccessDTO {
        
        private Long id;
        
        @NotBlank(message = "Folder path is required", groups = {CreateGroup.class, UpdateGroup.class})
        @Size(max = 1000, message = "Folder path cannot exceed 1000 characters", groups = {CreateGroup.class, UpdateGroup.class})
        private String folderPath;

        // Backend format: Access type enum
        @JsonSetter("accessType")
        private UnixGroupFolderAccess.AccessType accessType;

        // Backend format: Permissions as string
        private String permissions;

        // Frontend format: Individual permission booleans
        private Boolean readPermission;
        private Boolean writePermission;
        private Boolean executePermission;

        // Frontend format: Permissions as object (for JSON deserialization)
        @JsonIgnore
        private Object permissionsObject;

        @Builder.Default
        private Boolean recursive = false;

        private UserDTO createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /**
         * Convert entity to DTO
         */
        public static FolderAccessDTO fromEntity(UnixGroupFolderAccess folderAccess) {
            if (folderAccess == null) {
                return null;
            }

            return FolderAccessDTO.builder()
                    .id(folderAccess.getId())
                    .folderPath(folderAccess.getFolderPath())
                    .accessType(folderAccess.getAccessType())
                    .permissions(folderAccess.getPermissions())
                    .recursive(folderAccess.getRecursive())
                    .createdBy(convertUserToDTO(folderAccess.getCreatedBy()))
                    .createdAt(folderAccess.getCreatedAt())
                    .updatedAt(folderAccess.getUpdatedAt())
                    .build();
        }

        /**
         * Convert DTO to entity
         */
        public UnixGroupFolderAccess toEntity() {
            // Ensure permissions are normalized before creating entity
            normalizePermissions();
            
            return UnixGroupFolderAccess.builder()
                    .id(this.id)
                    .folderPath(this.folderPath)
                    .accessType(this.accessType)
                    .permissions(this.permissions)
                    .recursive(this.recursive)
                    .build();
        }

        /**
         * Determine access type from individual permission booleans
         */
        private UnixGroupFolderAccess.AccessType determineAccessTypeFromPermissions() {
            if (readPermission == null && writePermission == null && executePermission == null) {
                return UnixGroupFolderAccess.AccessType.READ_ONLY; // Default to read-only
            }
            
            boolean read = readPermission != null && readPermission;
            boolean write = writePermission != null && writePermission;
            boolean execute = executePermission != null && executePermission;
            
            return mapPermissionsToAccessType(read, write, execute);
        }
        
        /**
         * Map permission combinations to access type enum
         */
        private UnixGroupFolderAccess.AccessType mapPermissionsToAccessType(boolean read, boolean write, boolean execute) {
            // Map to the closest available enum value
            if (read && write && execute) {
                return UnixGroupFolderAccess.AccessType.READ_WRITE_EXECUTE;
            } else if (read && write) {
                return UnixGroupFolderAccess.AccessType.READ_WRITE;
            } else if (read && execute) {
                // READ + EXECUTE maps to READ_WRITE_EXECUTE (closest available)
                return UnixGroupFolderAccess.AccessType.READ_WRITE_EXECUTE;
            } else if (write && execute) {
                // WRITE + EXECUTE maps to READ_WRITE_EXECUTE (closest available)
                return UnixGroupFolderAccess.AccessType.READ_WRITE_EXECUTE;
            } else if (read) {
                return UnixGroupFolderAccess.AccessType.READ_ONLY;
            } else if (write) {
                return UnixGroupFolderAccess.AccessType.WRITE_ONLY;
            } else if (execute) {
                return UnixGroupFolderAccess.AccessType.EXECUTE_ONLY;
            } else {
                return UnixGroupFolderAccess.AccessType.READ_ONLY; // Default to read-only
            }
        }

        /**
         * Build permission string from individual permission booleans
         */
        private String buildPermissionString() {
            boolean read = readPermission != null && readPermission;
            boolean write = writePermission != null && writePermission;
            boolean execute = executePermission != null && executePermission;

            return (read ? "r" : "-") +
                    (write ? "w" : "-") +
                    (execute ? "x" : "-");
        }

        /**
         * Convert individual permissions to accessType and permissions string
         * This method should be called after deserialization to normalize the data
         * Prioritizes individual permissions over accessType when both are present
         */
        public void normalizePermissions() {
            // If we have individual permissions, use them to determine accessType (overrides any existing accessType)
            if (readPermission != null || writePermission != null || executePermission != null) {
                this.accessType = determineAccessTypeFromPermissions();
                this.permissions = buildPermissionString();
            } else if (accessType != null && permissions == null) {
                // If we only have accessType, derive permissions string from it
                this.permissions = accessType.getPermissionString();
            }
        }

        /**
         * Custom setter for accessType from frontend JSON
         * This handles the case where frontend sends simplified names like "READ" instead of "READ_ONLY"
         */
        @JsonSetter("accessType")
        public void setAccessTypeFromJson(Object accessTypeValue) {
            if (accessTypeValue instanceof String accessTypeStr) {
                this.accessType = mapFrontendAccessTypeToEnum(accessTypeStr);
            } else if (accessTypeValue instanceof UnixGroupFolderAccess.AccessType accessTypeEnum) {
                this.accessType = accessTypeEnum;
            }
        }

        /**
         * Map frontend access type strings to backend enum values
         */
        private UnixGroupFolderAccess.AccessType mapFrontendAccessTypeToEnum(String frontendAccessType) {
            if (frontendAccessType == null) {
                return null;
            }
            
            return switch (frontendAccessType.toUpperCase()) {
                case "READ" -> UnixGroupFolderAccess.AccessType.READ_ONLY;
                case "WRITE" -> UnixGroupFolderAccess.AccessType.WRITE_ONLY;
                case "EXECUTE" -> UnixGroupFolderAccess.AccessType.EXECUTE_ONLY;
                case "READ_WRITE" -> UnixGroupFolderAccess.AccessType.READ_WRITE;
                case "READ_EXECUTE" -> UnixGroupFolderAccess.AccessType.READ_ONLY; // Map to closest match
                case "WRITE_EXECUTE" -> UnixGroupFolderAccess.AccessType.WRITE_ONLY; // Map to closest match
                case "READ_WRITE_EXECUTE", "FULL" -> UnixGroupFolderAccess.AccessType.READ_WRITE_EXECUTE;
                // Handle backend enum names directly
                case "READ_ONLY" -> UnixGroupFolderAccess.AccessType.READ_ONLY;
                case "WRITE_ONLY" -> UnixGroupFolderAccess.AccessType.WRITE_ONLY;
                case "EXECUTE_ONLY" -> UnixGroupFolderAccess.AccessType.EXECUTE_ONLY;
                default -> {
                    log.warn("Unknown access type: {}, defaulting to READ_ONLY", frontendAccessType);
                    yield UnixGroupFolderAccess.AccessType.READ_ONLY;
                }
            };
        }

        /**
         * Custom setter for permissions object from frontend JSON
         * This handles the case where frontend sends: "permissions": {"read": true, "write": false, "execute": false}
         */
        @com.fasterxml.jackson.annotation.JsonSetter("permissions")
        public void setPermissionsFromJson(Object permissionsValue) {
            if (permissionsValue instanceof String string) {
                // Backend format: permissions as string
                this.permissions = string;
            } else if (permissionsValue instanceof Map) {
                // Frontend format: permissions as object
                @SuppressWarnings("unchecked")
                Map<String, Object> permissionsMap = (Map<String, Object>) permissionsValue;
                extractPermissionsFromMap(permissionsMap);
            }
        }

        /**
         * Handle permissions object from frontend (e.g., {"read": true, "write": false, "execute": false})
         * This method can be called to extract individual permissions from a permissions object
         */
        public void extractPermissionsFromObject(Object permissionsObj) {
            if (permissionsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> permissionsMap = (Map<String, Object>) permissionsObj;
                extractPermissionsFromMap(permissionsMap);
            }
        }

        /**
         * Helper method to extract permissions from a Map and set individual permission fields
         * This eliminates code duplication between setPermissionsFromJson and extractPermissionsFromObject
         */
        private void extractPermissionsFromMap(Map<String, Object> permissionsMap) {
            Object read = permissionsMap.get("read");
            Object write = permissionsMap.get("write");
            Object execute = permissionsMap.get("execute");
            
            if (read instanceof Boolean readBool) {
                this.readPermission = readBool;
            }
            if (write instanceof Boolean writeBool) {
                this.writePermission = writeBool;
            }
            if (execute instanceof Boolean executeBool) {
                this.executePermission = executeBool;
            }
            
            // Normalize after extraction
            normalizePermissions();
        }

        /**
         * Validate folder path (delegates to entity method)
         */
        public boolean isValidFolderPath() {
            // Create a temporary entity to use its validation logic
            UnixGroupFolderAccess tempEntity = UnixGroupFolderAccess.builder()
                    .folderPath(this.folderPath)
                    .build();
            return tempEntity.isValidFolderPath();
        }

        /**
         * Get normalized folder path (delegates to entity method)
         */
        public String getNormalizedFolderPath() {
            // Create a temporary entity to use its normalization logic
            UnixGroupFolderAccess tempEntity = UnixGroupFolderAccess.builder()
                    .folderPath(this.folderPath)
                    .build();
            return tempEntity.getNormalizedFolderPath();
        }
    }

    /**
     * Result of synchronization operation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncResult {
        private int groupsAdded;
        private int groupsUpdated;
        private int groupsRemoved;
        private int groupsSkipped;
        private List<String> errors;
        private boolean success;

        /**
         * Create a successful sync result
         */
        public static SyncResult success(int added, int updated, int removed, int skipped) {
            return SyncResult.builder()
                    .groupsAdded(added)
                    .groupsUpdated(updated)
                    .groupsRemoved(removed)
                    .groupsSkipped(skipped)
                    .success(true)
                    .build();
        }

        /**
         * Create a failed sync result
         */
        public static SyncResult failure(List<String> errors) {
            return SyncResult.builder()
                    .errors(errors)
                    .success(false)
                    .build();
        }
    }
}