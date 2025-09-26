package com.verlake.dam.utils;

import com.verlake.dam.enums.UnixServerType;
import com.verlake.dam.entity.assets.Asset;

/**
 * Builder for Unix commands that are compatible across different server types
 */
public class UnixCommandBuilder {

    /**
     * Private constructor to prevent instantiation of utility class
     */
    private UnixCommandBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Build a find command for folder suggestions based on server type
     */
    public static String buildFolderFindCommand(Asset asset, String path) {
        UnixServerType serverType = determineServerType(asset);
        String escapedPath = escapePath(path);
        
        return switch (serverType) {
            case LINUX, FREEBSD -> 
                String.format("find '%s' -maxdepth 1 -type d 2>/dev/null | head -50", escapedPath);
            
            case UNIX -> 
                // Use more compatible approach for generic Unix
                String.format("find '%s' -type d -prune 2>/dev/null | head -50", escapedPath);
            
            case SOLARIS -> 
                // Solaris-specific approach (works on both old and new versions)
                String.format("find '%s' -type d -prune 2>/dev/null | head -50", escapedPath);
        };
    }
    
    /**
     * Build a detailed ls command for folder suggestions with file information
     */
    public static String buildDetailedFolderListCommand(Asset asset, String path) {
        UnixServerType serverType = determineServerType(asset);
        String escapedPath = escapePath(path);
        
        return switch (serverType) {
            case LINUX, FREEBSD, UNIX, SOLARIS -> 
                String.format("ls -la '%s' 2>/dev/null | head -50", escapedPath);
        };
    }
    
    /**
     * Build a find command for recursive folder search
     */
    public static String buildRecursiveFolderFindCommand(Asset asset, String path, int maxDepth) {
        UnixServerType serverType = determineServerType(asset);
        String escapedPath = escapePath(path);
        
        return switch (serverType) {
            case LINUX, FREEBSD -> 
                String.format("find '%s' -maxdepth %d -type d 2>/dev/null | head -100", escapedPath, maxDepth);
            
            case UNIX, SOLARIS -> 
                // Use find with -prune for better compatibility
                String.format("find '%s' -type d 2>/dev/null | head -100", escapedPath);
        };
    }
    
    /**
     * Build a command to get groups based on server type
     */
    public static String buildGetGroupsCommand(Asset asset) {
        UnixServerType serverType = determineServerType(asset);
        
        return switch (serverType) {
            case LINUX, FREEBSD, UNIX -> 
                "getent group | cut -d: -f1,3,4 | sort";
            
            case SOLARIS -> 
                // Solaris might not have getent, use /etc/group directly
                "cat /etc/group | cut -d: -f1,3,4 | sort";
        };
    }
    
    /**
     * Build a command to check if ACL is supported
     */
    public static String buildAclCheckCommand(Asset asset) {
        UnixServerType serverType = determineServerType(asset);
        
        return switch (serverType) {
            case LINUX, FREEBSD -> 
                "which setfacl";
            
            case UNIX, SOLARIS -> 
                // Some Unix systems might have different ACL tools
                "which setfacl || which chmod +a || echo 'not_supported'";
        };
    }
    
    /**
     * Determine server type from asset
     */
    private static UnixServerType determineServerType(Asset asset) {
        if (asset == null || asset.getUnixServerType() == null) {
            // Default to LINUX for safety if not specified
            return UnixServerType.LINUX;
        }
        return asset.getUnixServerType();
    }
    
    /**
     * Escape path for shell safety
     */
    private static String escapePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // Escape single quotes by replacing ' with '\''
        return path.replace("'", "'\"'\"'");
    }
    
    /**
     * Build a command to find folder suggestions based on partial path input
     * Examples:
     * - '/usr/g' -> finds folders starting with 'g' in /usr directory
     * - '/et' -> finds folders starting with 'et' in root directory (like /etc)
     * - '/var/log/a' -> finds folders starting with 'a' in /var/log directory
     */
    public static String buildPartialPathSuggestionCommand(Asset asset, String partialPath) {
        UnixServerType serverType = determineServerType(asset);
        
        // Parse the partial path to get parent directory and search pattern
        PathInfo pathInfo = parsePartialPath(partialPath);
        String parentDir = escapePath(pathInfo.parentDirectory);
        String pattern = pathInfo.searchPattern;
        
        return switch (serverType) {
            case LINUX, FREEBSD -> 
                buildLinuxPartialPathCommand(parentDir, pattern);
            
            case UNIX, SOLARIS -> 
                buildUnixPartialPathCommand(parentDir, pattern);
        };
    }
    
    /**
     * Build Linux-specific command for partial path suggestions
     */
    private static String buildLinuxPartialPathCommand(String parentDir, String pattern) {
        if (pattern.isEmpty()) {
            // No pattern, just list all directories in parent
            return String.format("ls -1d -la '%s'*/ 2>/dev/null | head -20", parentDir);
        } else {
            // Pattern provided, find directories that start with pattern
            return String.format("ls -1d -la '%s'%s*/ 2>/dev/null | head -20", parentDir, pattern);
        }
    }
    
    /**
     * Build Unix/Solaris-specific command for partial path suggestions
     */
    private static String buildUnixPartialPathCommand(String parentDir, String pattern) {
        if (pattern.isEmpty()) {
            // No pattern, just list all directories in parent
            return String.format("find '%s' -maxdepth 1 -type d ! -path '%s' 2>/dev/null | head -20", parentDir, parentDir);
        } else {
            // Pattern provided, find directories that start with pattern
            return String.format("find '%s' -maxdepth 1 -type d -name '%s*' 2>/dev/null | head -20", parentDir, pattern);
        }
    }
    
    /**
     * Build a more robust partial path command that works across all Unix variants
     */
    public static String buildUniversalPartialPathCommand(String partialPath) {
        PathInfo pathInfo = parsePartialPath(partialPath);
        String parentDir = escapePath(pathInfo.parentDirectory);
        String pattern = pathInfo.searchPattern;
        
        if (pattern.isEmpty()) {
            // List all directories in parent directory
            return String.format("for d in '%s'/*/; do [ -d \"$d\" ] && echo \"$d\" | sed 's|/$||'; done 2>/dev/null | head -20", parentDir);
        } else {
            // List directories that start with pattern
            return String.format("for d in '%s'/%s*/; do [ -d \"$d\" ] && echo \"$d\" | sed 's|/$||'; done 2>/dev/null | head -20", parentDir, pattern);
        }
    }
    
    /**
     * Parse partial path into parent directory and search pattern
     */
    private static PathInfo parsePartialPath(String partialPath) {
        if (partialPath == null || partialPath.isEmpty() || partialPath.equals("/")) {
            return new PathInfo("/", "");
        }
        
        // Normalize path (remove trailing slash if present)
        String normalizedPath = partialPath.endsWith("/") && partialPath.length() > 1 
            ? partialPath.substring(0, partialPath.length() - 1) 
            : partialPath;
        
        // Find the last slash to separate parent directory and pattern
        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        
        if (lastSlashIndex == -1) {
            // No slash found, treat as pattern in root directory
            return new PathInfo("/", normalizedPath);
        } else if (lastSlashIndex == 0) {
            // Slash at beginning, pattern is in root directory
            String pattern = normalizedPath.substring(1);
            return new PathInfo("/", pattern);
        } else {
            // Normal case: separate parent directory and pattern
            String parentDir = normalizedPath.substring(0, lastSlashIndex);
            String pattern = normalizedPath.substring(lastSlashIndex + 1);
            
            // Handle empty parent directory (shouldn't happen but be safe)
            if (parentDir.isEmpty()) {
                parentDir = "/";
            }
            
            return new PathInfo(parentDir, pattern);
        }
    }
    
    /**
     * Helper class to hold parsed path information
     */
    private static class PathInfo {
        final String parentDirectory;
        final String searchPattern;
        
        PathInfo(String parentDirectory, String searchPattern) {
            this.parentDirectory = parentDirectory;
            this.searchPattern = searchPattern != null ? searchPattern : "";
        }
    }
    
}
