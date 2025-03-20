package com.verlake.dam.controller.resource_owner;

import com.verlake.dam.entity.Asset;
import com.verlake.dam.entity.AssetCredential;
import com.verlake.dam.entity.dto.AssetCredentialDTO;
import com.verlake.dam.service.AssetService;
import com.verlake.dam.service.KeycloakService;
import com.verlake.dam.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import static com.verlake.dam.utils.Constants.AUTH_PROVIDER_KEYCLOAK;


@RestController
@RequestMapping("/api/resource_owner/assets")
public class OwnerAssetController {
    private final AssetService assetService;
    private final KeycloakService keycloakService;

    @Value("${auth.provider}")
    private String authProvider;

    @Autowired
    public OwnerAssetController(AssetService assetService, KeycloakService keycloakService) {
        this.assetService = assetService;
        this.keycloakService = keycloakService;
    }


    @GetMapping("/new-credentials")
    public ResponseEntity<List<AssetCredential>> getNewAssignedCredentials() {
        return ResponseEntity.ok(assetService.getNewAssignedCredentials());
    }

    @GetMapping("/credentials")
    public ResponseEntity<List<AssetCredential>> getAllAssetCredentials() {
        return ResponseEntity.ok(assetService.getAssignedCredentials());
    }

    @PostMapping("/credentials/{credentialId}")
    public ResponseEntity<Map<String, Object>> setCredentialInfo(@PathVariable Long credentialId, @RequestBody AssetCredentialDTO credentialInfo) {
        AssetCredential existingCredential = assetService.findCredentialById(credentialId);
        if (existingCredential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset Credential not found in database");
        }

        Asset asset = existingCredential.getAsset();
        String host = asset.getHostAddress();
        String username = credentialInfo.getUsername();
        String password = credentialInfo.getPassword();

        String jdbcUrlPrefix;
        switch (asset.getDatabaseType()) {
            case MYSQL:
                jdbcUrlPrefix = "jdbc:mysql://";
                break;
            case POSTGRESQL:
                jdbcUrlPrefix = "jdbc:postgresql://";
                break;
            case ORACLE:
                jdbcUrlPrefix = "jdbc:oracle:thin:@";
                break;
            case SQLSERVER:
                jdbcUrlPrefix = "jdbc:sqlserver://";
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported database type");
        }

        // Validate the database connection
        try (Connection connection = DriverManager.getConnection(jdbcUrlPrefix + host, username, password)) {
            if (connection != null) {
                if (authProvider.contains(AUTH_PROVIDER_KEYCLOAK.toLowerCase())) {
                    String userKey = keycloakService.getUserKey(CommonUtils.getKeycloakUserIdFromSession());
                    if(userKey != null && !userKey.isEmpty()) {
                        password = encryptPasswordWithKey(userKey, password);
                    }
                }
                existingCredential.setUsername(username);
                existingCredential.setPassword(password);
                assetService.saveCredential(existingCredential);
                return CommonUtils.getSuccessResponse();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection failed: Unknown error");
            }
        } catch (SQLException e) {
            // Connection failed
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection failed: " + e.getMessage());
        }
    }

    private String encryptPasswordWithKey(String userKey, String password) {
        try {
            return CommonUtils.encrypt(userKey, password);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to encrypt password", e);
        }
    }
}