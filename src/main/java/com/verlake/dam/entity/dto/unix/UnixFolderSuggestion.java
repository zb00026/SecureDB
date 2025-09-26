package com.verlake.dam.entity.dto.unix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Unix folder/file suggestions with detailed information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnixFolderSuggestion {
    
    /**
     * Full path to the folder/file
     */
    private String path;
    
    /**
     * Type of the item (directory, file, etc.)
     */
    private String type;
    
    /**
     * Owner of the folder/file
     */
    private String owner;
    
    /**
     * Group of the folder/file
     */
    private String group;
    
    /**
     * Size of the file in bytes (null for directories)
     */
    private Long size;
    
    /**
     * Permissions in ls -l format (e.g., "drwxr-xr-x")
     */
    private String permissions;
    
    /**
     * Last modified timestamp
     */
    private String lastModified;
    
    /**
     * Whether this is a directory
     */
    public boolean isDirectory() {
        return "directory".equals(type) || (permissions != null && permissions.startsWith("d"));
    }
    
    /**
     * Whether this is a file
     */
    public boolean isFile() {
        return "file".equals(type) || (permissions != null && permissions.startsWith("-"));
    }
}
