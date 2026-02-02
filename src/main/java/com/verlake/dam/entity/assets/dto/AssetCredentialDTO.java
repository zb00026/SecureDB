package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.enums.AssetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCredentialDTO {
    private Long assetId;
    private String username;
    private String password;
    private String sshKeyFile; // For Unix Server assets
    private String userAccessType; // For both database and SSH credentials
    private AssetType assetType; // To distinguish between DATABASE and UNIX_SERVER
    private String awsSecretsManagerKey; // AWS Secrets Manager key for credential storage
}