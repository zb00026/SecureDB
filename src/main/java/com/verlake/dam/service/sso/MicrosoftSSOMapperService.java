package com.verlake.dam.service.sso;

import com.verlake.dam.utils.Constants;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for configuring Microsoft SSO Identity Provider mappers in Keycloak.
 */
@Service
public class MicrosoftSSOMapperService extends BaseSSOMapperService {
    
    @Override
    protected String getIdpAlias() {
        return Constants.KEYCLOAK_IDP_MICROSOFT;
    }
    
    @Override
    protected String getIdpDisplayName() {
        return "Microsoft";
    }
    
    @Override
    protected List<MapperConfig> getMapperConfigs() {
        return List.of(
            // Username mapper - maps preferred_username to username
            createOidcAttributeMapper(
                "Microsoft Username", "Username",
                Constants.KEYCLOAK_MAPPER_PREFERRED_USERNAME, 
                Constants.KEYCLOAK_MAPPER_USERNAME,
                Constants.KEYCLOAK_MAPPER_SYNC_MODE_FORCE
            ),
            
            // Email mapper - maps preferred_username to email (Microsoft doesn't always return email claim)
            createOidcAttributeMapper(
                "Microsoft Email", "Email",
                Constants.KEYCLOAK_MAPPER_PREFERRED_USERNAME, 
                Constants.KEYCLOAK_MAPPER_EMAIL,
                Constants.KEYCLOAK_MAPPER_SYNC_MODE_FORCE
            ),
            
            // First name mapper - hardcoded value (Microsoft doesn't always return given_name)
            createHardcodedAttributeMapper(
                "Microsoft First Name", "First Name",
                "Microsoft",
                Constants.KEYCLOAK_MAPPER_FIRST_NAME,
                Constants.KEYCLOAK_MAPPER_SYNC_MODE_FORCE
            ),
            
            // Last name mapper - hardcoded value
            createHardcodedAttributeMapper(
                "Microsoft Last Name", "Last Name",
                "User", 
                Constants.KEYCLOAK_MAPPER_LAST_NAME,
                Constants.KEYCLOAK_MAPPER_SYNC_MODE_FORCE
            )
        );
    }
}
