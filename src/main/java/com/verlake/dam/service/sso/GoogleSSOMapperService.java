package com.verlake.dam.service.sso;

import com.verlake.dam.utils.Constants;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for configuring Google SSO Identity Provider mappers in Keycloak.
 */
@Service
public class GoogleSSOMapperService extends BaseSSOMapperService {
    
    @Override
    protected String getIdpAlias() {
        return Constants.KEYCLOAK_IDP_GOOGLE;
    }
    
    @Override
    protected String getIdpDisplayName() {
        return "Google";
    }
    
    @Override
    protected List<MapperConfig> getMapperConfigs() {
        return List.of(
            // Username mapper - maps email to username
            createOidcAttributeMapper(
                "Google Username", "Username",
                Constants.KEYCLOAK_MAPPER_EMAIL, 
                Constants.KEYCLOAK_MAPPER_USERNAME,
                Constants.KEYCLOAK_MAPPER_SYNC_MODE_INHERIT
            ),
            
            // Email mapper
            createOidcAttributeMapper(
                "Google Email", "Email",
                Constants.KEYCLOAK_MAPPER_EMAIL, 
                Constants.KEYCLOAK_MAPPER_EMAIL,
                Constants.KEYCLOAK_MAPPER_SYNC_MODE_INHERIT
            ),
            
            // First name mapper - uses Google-specific mapper
            createGoogleFirstNameMapper(),
            
            // Last name mapper - hardcoded value
            createHardcodedAttributeMapper(
                "Google Last Name", "Last Name",
                Constants.KEYCLOAK_MAPPER_LAST_NAME_DEFAULT, 
                Constants.KEYCLOAK_MAPPER_LAST_NAME,
                Constants.KEYCLOAK_MAPPER_SYNC_MODE_FORCE
            )
        );
    }
    
    /**
     * Creates Google-specific first name mapper using google-user-attribute-mapper
     */
    private MapperConfig createGoogleFirstNameMapper() {
        return new MapperConfig(
            "Google First Name",
            "First Name",
            Constants.KEYCLOAK_IDP_MAPPER_GOOGLE_USER_ATTRIBUTE,
            Map.of(
                Constants.KEYCLOAK_MAPPER_SYNC_MODE, Constants.KEYCLOAK_MAPPER_SYNC_MODE_FORCE,
                Constants.KEYCLOAK_MAPPER_JSON_FIELD, Constants.KEYCLOAK_MAPPER_GIVEN_NAME,
                Constants.KEYCLOAK_MAPPER_USER_ATTRIBUTE_GOOGLE, Constants.KEYCLOAK_MAPPER_FIRST_NAME
            )
        );
    }
}
