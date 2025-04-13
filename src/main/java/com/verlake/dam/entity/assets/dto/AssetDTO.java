package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;

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
    private String hostAddress;
    private String fetchTemplate;
    private AccessRequest accessRequest;
    private List<User> owners;
    private List<User> approvers;
}