package com.verlake.dam.entity.dto.unix;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for folder access permission requests
 */
@Data
public class FolderAccessRequestDTO {
    
    /**
     * The folder path to set permissions for
     */
    @NotBlank(message = "Folder path is required")
    private String folderPath;
    
    /**
     * Whether to grant read permission
     */
    @NotNull(message = "Read permission is required")
    private Boolean readPermission;
    
    /**
     * Whether to grant write permission
     */
    @NotNull(message = "Write permission is required")
    private Boolean writePermission;
    
    /**
     * Whether to grant execute permission
     */
    @NotNull(message = "Execute permission is required")
    private Boolean executePermission;
    
    /**
     * Whether to apply permissions recursively to subdirectories
     */
    private Boolean recursive = false;
    
    /**
     * Unix group ID to apply permissions to
     */
    @NotNull(message = "Group ID is required")
    private Long groupId;
    
    /**
     * Asset ID where the folder is located
     */
    @NotNull(message = "Asset ID is required")
    private Long assetId;
}

