package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.UnixServerType;
import com.verlake.dam.enums.LockType;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssetDTO {
    private Long id;
    private String name;
    private String description;
    private AssetType type;
    private DatabaseType databaseType;
    private UnixServerType unixServerType;
    private String hostAddress;
    private String portNumber;
    private String databaseName;
    private String fetchTemplate;
    private boolean locked;
    private LockType lockType;
    private AccessRequest accessRequest;
    private List<User> owners;
    private List<User> approvers;
    
    /**
     * Convert Asset entity to DTO
     */
    public static AssetDTO fromEntity(Asset asset) {
        if (asset == null) {
            return null;
        }
        
        return AssetDTO.builder()
                .id(asset.getId())
                .name(asset.getName())
                .description(asset.getDescription())
                .type(asset.getType())
                .databaseType(asset.getDatabaseType())
                .unixServerType(asset.getUnixServerType())
                .hostAddress(asset.getHostAddress())
                .portNumber(asset.getPortNumber())
                .databaseName(asset.getDatabaseName())
                .locked(asset.isLocked())
                .lockType(asset.getLockType())
                // Note: AccessRequest, owners, and approvers are not included to avoid circular references
                // These can be loaded separately if needed
                .build();
    }
}