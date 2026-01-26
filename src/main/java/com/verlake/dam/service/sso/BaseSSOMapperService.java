package com.verlake.dam.service.sso;

import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.utils.Constants;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Map;

/**
 * Base abstract class for SSO Identity Provider mapper services.
 * Provides common functionality for configuring identity provider mappers in Keycloak.
 */
public abstract class BaseSSOMapperService {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    @Autowired
    protected KeycloakService keycloakService;
    
    /**
     * Get the identity provider alias (e.g., "google", "microsoft")
     */
    protected abstract String getIdpAlias();
    
    /**
     * Get the display name for logging (e.g., "Google", "Microsoft")
     */
    protected abstract String getIdpDisplayName();
    
    /**
     * Get the list of mapper configurations to create
     */
    protected abstract List<MapperConfig> getMapperConfigs();
    
    /**
     * Configures Identity Provider mappers.
     * Creates mappers for username, email, first name, and last name.
     */
    public void configureMappers() {
        try {
            RealmResource realm = keycloakService.getRealmInstance();
            
            // Check if identity provider exists
            var identityProviders = realm.identityProviders().findAll();
            boolean idpExists = identityProviders.stream()
                .anyMatch(idp -> getIdpAlias().equals(idp.getAlias()));
            
            if (!idpExists) {
                log.warn("{} Identity Provider not found, skipping mapper configuration", getIdpDisplayName());
                return;
            }
            
            log.info("{} Identity Provider found, configuring mappers...", getIdpDisplayName());
            
            // Create all mappers
            IdentityProviderResource idpResource = realm.identityProviders().get(getIdpAlias());
            var existingMappers = idpResource.getMappers();
            
            for (MapperConfig config : getMapperConfigs()) {
                createMapper(idpResource, existingMappers, config);
            }
            
            log.info("{} Identity Provider mappers configured successfully", getIdpDisplayName());
            
        } catch (ForbiddenException e) {
            log.warn("Permission denied (403) when configuring {} mappers. " +
                    "The Keycloak service account needs 'view-identity-providers' and 'manage-identity-providers' roles. " +
                    "Skipping mapper configuration. Error: {}", getIdpDisplayName(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to configure {} mappers: {}", getIdpDisplayName(), e.getMessage(), e);
        }
    }
    
    /**
     * Creates a single mapper if it doesn't already exist.
     */
    protected void createMapper(IdentityProviderResource idpResource, 
                                List<IdentityProviderMapperRepresentation> existingMappers,
                                MapperConfig config) {
        try {
            log.info("Creating/updating {} {} mapper...", getIdpDisplayName(), config.displayName());
            
            // Check if mapper already exists
            boolean exists = existingMappers.stream()
                .anyMatch(m -> config.name().equals(m.getName()));
            
            if (exists) {
                log.info("{} {} mapper already exists, skipping creation", getIdpDisplayName(), config.displayName());
                return;
            }
            
            // Create new mapper
            IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
            mapper.setName(config.name());
            mapper.setIdentityProviderAlias(getIdpAlias());
            mapper.setIdentityProviderMapper(config.mapperType());
            mapper.setConfig(config.configMap());
            
            idpResource.addMapper(mapper);
            log.info("{} {} mapper created successfully", getIdpDisplayName(), config.displayName());
            
        } catch (Exception e) {
            log.error("Failed to create/update {} {} mapper: {}", 
                    getIdpDisplayName(), config.displayName(), e.getMessage(), e);
        }
    }
    
    /**
     * Record to hold mapper configuration
     */
    public record MapperConfig(
        String name,
        String displayName,
        String mapperType,
        Map<String, String> configMap
    ) {}
    
    /**
     * Helper method to create OIDC user attribute mapper config
     */
    protected MapperConfig createOidcAttributeMapper(String name, String displayName, 
                                                      String claim, String userAttribute, 
                                                      String syncMode) {
        return new MapperConfig(
            name,
            displayName,
            Constants.KEYCLOAK_IDP_MAPPER_OIDC_USER_ATTRIBUTE,
            Map.of(
                Constants.KEYCLOAK_MAPPER_SYNC_MODE, syncMode,
                Constants.KEYCLOAK_MAPPER_CLAIM, claim,
                Constants.KEYCLOAK_MAPPER_USER_ATTRIBUTE, userAttribute
            )
        );
    }
    
    /**
     * Helper method to create hardcoded attribute mapper config
     */
    protected MapperConfig createHardcodedAttributeMapper(String name, String displayName,
                                                           String attributeValue, String attribute,
                                                           String syncMode) {
        return new MapperConfig(
            name,
            displayName,
            Constants.KEYCLOAK_IDP_MAPPER_HARDCODED_ATTRIBUTE,
            Map.of(
                Constants.KEYCLOAK_MAPPER_SYNC_MODE, syncMode,
                Constants.KEYCLOAK_MAPPER_ATTRIBUTE_VALUE, attributeValue,
                Constants.KEYCLOAK_MAPPER_ATTRIBUTE, attribute
            )
        );
    }
}

