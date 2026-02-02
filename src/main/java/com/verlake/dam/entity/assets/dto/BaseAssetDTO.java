package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.LockType;
import com.verlake.dam.enums.UnixServerType;
import lombok.Data;

import java.util.List;

@Data
public abstract class BaseAssetDTO {
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
    private Integer recordCountLimit;
    private List<User> owners;
    private List<User> approvers;
    
    /**
     * Common method to populate base fields from Asset entity
     */
    protected void populateBaseFields(Asset asset) {
        if (asset != null) {
            this.id = asset.getId();
            this.name = asset.getName();
            this.description = asset.getDescription();
            this.type = asset.getType();
            this.databaseType = asset.getDatabaseType();
            this.unixServerType = asset.getUnixServerType();
            this.hostAddress = asset.getHostAddress();
            this.portNumber = asset.getPortNumber();
            this.databaseName = asset.getDatabaseName();
            this.locked = asset.isLocked();
            this.lockType = asset.getLockType();
            this.recordCountLimit = asset.getRecordCountLimit();
        }
    }
}
