package com.verlake.dam.entity.dto.unix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for ACL application results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclApplicationResultDTO {
    
    private boolean success;
    private Long groupId;
    private String groupName;
    private Long assetId;
    private String error;
    private List<PermissionApplicationResult> appliedPermissions;
    private int totalPermissionsApplied;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionApplicationResult {
        private String folderPath;
        private String accessType;
        private String permissions;
        private boolean recursive;
        private String command;
        private String output;
        private boolean success;
    }
}
