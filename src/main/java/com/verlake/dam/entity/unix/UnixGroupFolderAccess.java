package com.verlake.dam.entity.unix;

import com.verlake.dam.entity.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing folder access permissions for a Unix group
 */
@Entity
@Table(name = "unix_group_folder_access",
       uniqueConstraints = @UniqueConstraint(name = "uk_unix_group_folder_access_group_folder", 
                                           columnNames = {"group_id", "folder_path"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnixGroupFolderAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_unix_group_folder_access_group"))
    private UnixGroup unixGroup;

    @Column(name = "folder_path", nullable = false, length = 1000)
    private String folderPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 20)
    private AccessType accessType;

    @Column(name = "permissions", nullable = false, length = 10)
    private String permissions;

    @Column(name = "is_recursive", nullable = false, columnDefinition = "TINYINT(1)")
    @Builder.Default
    private Boolean recursive = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_unix_group_folder_access_created_by"))
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Access types for folder permissions
     */
    public enum AccessType {
        READ_ONLY("r"),
        READ_WRITE("rw"),
        READ_WRITE_EXECUTE("rwx"),
        WRITE_ONLY("w"),
        EXECUTE_ONLY("x");

        private final String permissionString;

        AccessType(String permissionString) {
            this.permissionString = permissionString;
        }

        public String getPermissionString() {
            return permissionString;
        }

        /**
         * Get ACL permissions for this access type
         */
        public String getAclPermissions() {
            switch (this) {
                case READ_ONLY:
                    return "r";
                case READ_WRITE:
                    return "rw";
                case READ_WRITE_EXECUTE:
                    return "rwx";
                case WRITE_ONLY:
                    return "w";
                case EXECUTE_ONLY:
                    return "x";
                default:
                    return "r";
            }
        }

        /**
         * Get numeric permissions for this access type
         */
        public String getNumericPermissions() {
            switch (this) {
                case READ_ONLY:
                    return "4";
                case READ_WRITE:
                    return "6";
                case READ_WRITE_EXECUTE:
                    return "7";
                case WRITE_ONLY:
                    return "2";
                case EXECUTE_ONLY:
                    return "1";
                default:
                    return "4";
            }
        }
    }

    /**
     * Validate folder path format
     */
    public boolean isValidFolderPath() {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return false;
        }
        
        // Check if path starts with / (absolute path)
        if (!folderPath.startsWith("/")) {
            return false;
        }
        
        // Check for invalid characters
        return !folderPath.contains("..") && !folderPath.contains("//");
    }

    /**
     * Get normalized folder path
     */
    public String getNormalizedFolderPath() {
        if (folderPath == null) {
            return null;
        }
        
        String normalized = folderPath.trim();
        
        // Ensure it ends with / for directories
        if (!normalized.endsWith("/") && !normalized.endsWith("*")) {
            normalized += "/";
        }
        
        return normalized;
    }

    /**
     * Check if this access allows recursive permissions
     */
    public boolean allowsRecursive() {
        return recursive != null && recursive;
    }

    /**
     * Get ACL command for setting permissions
     */
    public String getAclCommand(String groupName) {
        StringBuilder command = new StringBuilder();
        
        if (allowsRecursive()) {
            command.append("sudo setfacl -R -m g:").append(groupName).append(":").append(accessType.getAclPermissions());
        } else {
            command.append("sudo setfacl -m g:").append(groupName).append(":").append(accessType.getAclPermissions());
        }
        
        command.append(" ").append(getNormalizedFolderPath());
        
        return command.toString();
    }

    /**
     * Get ACL command for removing permissions
     */
    public String getRemoveAclCommand(String groupName) {
        StringBuilder command = new StringBuilder();
        
        if (allowsRecursive()) {
            command.append("setfacl -R -x g:").append(groupName);
        } else {
            command.append("setfacl -x g:").append(groupName);
        }
        
        command.append(" ").append(getNormalizedFolderPath());
        
        return command.toString();
    }
}
