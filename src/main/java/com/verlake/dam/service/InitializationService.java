package com.verlake.dam.service;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.auth.GlobalAuthProviderService;
import com.verlake.dam.utils.Constants;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InitializationService {
    
    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private KeycloakService keycloakService;
    
    @Autowired
    private GlobalAuthProviderService globalAuthProviderService;
    
    @Value("${KEYCLOAK_ADMIN_EMAIL_ADDR:}")
    private String adminEmail;
    
    @Value("${keycloak.2fa.enabled:true}")
    private boolean enabled2FA;
    
    @PostConstruct
    public void initializeKeycloakConfiguration() {
        try {
            log.info("Starting Keycloak configuration initialization...");
            
            // 1. Update admin email in database and Keycloak
            updateAdminEmail();
            
            // 2. Configure 2FA if enabled
            configure2FA();
            
            // 3. Configure Identity Provider mappers for SSO
            configureIdentityProviderMappers();
            
            log.info("Keycloak configuration initialization completed successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Keycloak configuration: {}", e.getMessage(), e);
        }
    }
    
    private void updateAdminEmail() {
        try {
            if (StringUtils.hasText(adminEmail)) {
                log.info("Updating admin user email configuration...");
                
                // Update admin email in database
                updateAdminEmailInDatabase();
                
                // Update admin email in Keycloak if using Keycloak auth provider
                if (globalAuthProviderService.isKeycloakProvider() && keycloakService != null) {
                    updateAdminEmailInKeycloak();
                }
            } else {
                log.info("KEYCLOAK_ADMIN_EMAIL_ADDR not set, skipping admin email update");
            }
        } catch (Exception e) {
            log.error("Failed to update admin email: {}", e.getMessage(), e);
        }
    }
    
    private void updateAdminEmailInDatabase() {
        try {
            Optional<User> adminUser = userRepository.findById(1L);
            if (adminUser.isPresent()) {
                User user = adminUser.get();
                String currentEmail = user.getEmail();
                
                if (!adminEmail.equals(currentEmail)) {
                    log.info("Updating admin user email in database from '{}' to '{}'", currentEmail, adminEmail);
                    user.setEmail(adminEmail);
                    userRepository.save(user);
                    log.info("Admin user email updated in database successfully");
                } else {
                    log.info("Admin user email in database is already set to '{}'", adminEmail);
                }
            } else {
                log.warn("Admin user with ID 1 not found in database");
            }
        } catch (Exception e) {
            log.error("Failed to update admin user email in database: {}", e.getMessage(), e);
        }
    }
    
    private void updateAdminEmailInKeycloak() {
        try {
            log.info("Updating admin user email in Keycloak...");
            
            RealmResource realm = keycloakService.getRealmInstance();
            List<UserRepresentation> users = realm.users().search("admin", 0, 1);
            
            if (!users.isEmpty()) {
                UserRepresentation adminUser = users.get(0);
                String currentEmail = adminUser.getEmail();
                
                if (!adminEmail.equals(currentEmail)) {
                    log.info("Updating admin user email in Keycloak from '{}' to '{}'", currentEmail, adminEmail);
                    adminUser.setEmail(adminEmail);
                    UserResource userResource = realm.users().get(adminUser.getId());
                    userResource.update(adminUser);
                    log.info("Admin user email updated in Keycloak successfully");
                } else {
                    log.info("Admin user email in Keycloak is already set to '{}'", adminEmail);
                }
            } else {
                log.warn("Admin user not found in Keycloak");
            }
        } catch (Exception e) {
            log.error("Failed to update admin user email in Keycloak: {}", e.getMessage(), e);
        }
    }
    
    private void configure2FA() {
        try {
            log.info("Configuring 2FA in Keycloak - Enabled: {}", enabled2FA);
            
            // Only configure 2FA if using Keycloak auth provider
            if (!globalAuthProviderService.isKeycloakProvider()) {
                log.info("Auth provider is not Keycloak, skipping 2FA configuration");
                return;
            }
            
            // Check if auth provider is SSO - if so, disable 2FA
            if (globalAuthProviderService.isSSOProvider()) {
                log.info("Auth provider is SSO, disabling 2FA for SSO users");
                disable2FA();
            } else if (enabled2FA) {
                enable2FA();
            } else {
                disable2FA();
            }
            
        } catch (Exception e) {
            log.error("Failed to configure 2FA: {}", e.getMessage(), e);
        }
    }
    
    private void enable2FA() {
        try {
            log.info("Enabling 2FA in Keycloak realm");
            
            RealmResource realm = keycloakService.getRealmInstance();
            configureOtpPolicy(realm);
            
            // Configure authentication flows to make OTP required
            configureAuthenticationFlowsFor2FA(realm);
            
            log.info("2FA enabled successfully in Keycloak realm");
            
        } catch (Exception e) {
            log.error("Failed to enable 2FA in Keycloak: {}", e.getMessage(), e);
        }
    }
    
    private void configureOtpPolicy(RealmResource realm) {
        try {
            log.info("Configuring OTP policy in Keycloak realm");
            
            RealmRepresentation realmRep = realm.toRepresentation();
            realmRep.setOtpPolicyType("totp");
            realmRep.setOtpPolicyAlgorithm("HmacSHA1");
            realmRep.setOtpPolicyInitialCounter(0);
            realmRep.setOtpPolicyDigits(6);
            realmRep.setOtpPolicyLookAheadWindow(1);
            realmRep.setOtpPolicyPeriod(30);
            realmRep.setOtpPolicyCodeReusable(false);
            realmRep.setBrowserFlow(Constants.KEYCLOAK_FLOW_BROWSER);
            
            realm.update(realmRep);
            log.info("OTP policy configured successfully");
            
        } catch (Exception e) {
            log.error("Failed to configure OTP policy: {}", e.getMessage(), e);
        }
    }
    
    private void configureAuthenticationFlowsFor2FA(RealmResource realm) {
        try {
            log.info("Configuring authentication flows for mandatory 2FA...");
            
            // Configure both flows to be Required
            updateAuthenticationFlow(realm, Constants.KEYCLOAK_FLOW_DIRECT_GRANT, Constants.KEYCLOAK_EXECUTION_DIRECT_GRANT_CONDITIONAL_OTP, Constants.KEYCLOAK_REQUIREMENT_REQUIRED);
            updateAuthenticationFlow(realm, Constants.KEYCLOAK_FLOW_BROWSER, Constants.KEYCLOAK_EXECUTION_BROWSER_CONDITIONAL_OTP, Constants.KEYCLOAK_REQUIREMENT_REQUIRED);
            
            log.info("Authentication flows configured for mandatory 2FA");
            
        } catch (Exception e) {
            log.error("Failed to configure authentication flows for 2FA: {}", e.getMessage(), e);
        }
    }
    
    private void configureAuthenticationFlowsFor2FADisable(RealmResource realm) {
        try {
            log.info("Configuring authentication flows to disable mandatory 2FA...");
            
            // Set both flows back to CONDITIONAL
            updateAuthenticationFlow(realm, Constants.KEYCLOAK_FLOW_DIRECT_GRANT, Constants.KEYCLOAK_EXECUTION_DIRECT_GRANT_CONDITIONAL_OTP, Constants.KEYCLOAK_REQUIREMENT_CONDITIONAL);
            updateAuthenticationFlow(realm, Constants.KEYCLOAK_FLOW_BROWSER, Constants.KEYCLOAK_EXECUTION_BROWSER_CONDITIONAL_OTP, Constants.KEYCLOAK_REQUIREMENT_CONDITIONAL);
            
            log.info("Authentication flows configured to disable mandatory 2FA");
            
        } catch (Exception e) {
            log.error("Failed to configure authentication flows to disable 2FA: {}", e.getMessage(), e);
        }
    }
    
    private void updateAuthenticationFlow(RealmResource realm, String flowAlias, String executionName, String requirement) {
        try {
            log.info("Updating {} - {} to {}", flowAlias, executionName, requirement);
            
            // Get all executions for the specified flow
            var executions = realm.flows().getExecutions(flowAlias);
            boolean found = false;
            
            for (var execution : executions) {
                if (executionName.equals(execution.getDisplayName())) {
                    found = true;
                    execution.setRequirement(requirement);
                    realm.flows().updateExecutions(flowAlias, execution);
                    log.info("Set {} - {} requirement to {}", flowAlias, executionName, requirement);
                    break;
                }
            }
            
            if (!found) {
                log.warn("{} - {} execution not found", flowAlias, executionName);
            }
            
        } catch (Exception e) {
            log.error("Failed to update {} - {} to {}: {}", flowAlias, executionName, requirement, e.getMessage(), e);
        }
    }
    
    private void disable2FA() {
        try {
            log.info("Disabling 2FA in Keycloak realm");
            
            RealmResource realm = keycloakService.getRealmInstance();
            configureOtpPolicy(realm);
            
            // Set authentication flows back to CONDITIONAL
            configureAuthenticationFlowsFor2FADisable(realm);
            
            log.info("2FA disabled successfully in Keycloak realm");
            
        } catch (Exception e) {
            log.error("Failed to disable 2FA in Keycloak: {}", e.getMessage(), e);
        }
    }
    
    private void configureIdentityProviderMappers() {
        try {
            log.info("Configuring Identity Provider Mappers...");
            
            // Only configure mappers if using Keycloak SSO
            if (!globalAuthProviderService.isSSOProvider()) {
                log.info("Auth provider is not SSO, skipping identity provider mapper configuration");
                return;
            }
            
            log.info("Auth provider is SSO, configuring Google Identity Provider mappers");
            configureGoogleMappers();
            
        } catch (Exception e) {
            log.error("Failed to configure Identity Provider Mappers: {}", e.getMessage(), e);
        }
    }
    
    private void configureGoogleMappers() {
        try {
            RealmResource realm = keycloakService.getRealmInstance();
            
                    // Check if Google identity provider exists
                    var identityProviders = realm.identityProviders().findAll();
                    boolean googleIdpExists = identityProviders.stream()
                        .anyMatch(idp -> Constants.KEYCLOAK_IDP_GOOGLE.equals(idp.getAlias()));
            
            if (!googleIdpExists) {
                log.warn("Google Identity Provider not found, skipping mapper configuration");
                return;
            }
            
            log.info("Google Identity Provider found, configuring mappers...");
            
            // Create mappers
            createGoogleUsernameMapper(realm);
            createGoogleEmailMapper(realm);
            createGoogleFirstNameMapper(realm);
            createGoogleLastNameMapper(realm);
            
        } catch (Exception e) {
            log.error("Failed to configure Google mappers: {}", e.getMessage(), e);
        }
    }
    
    private void createGoogleUsernameMapper(RealmResource realm) {
        try {
            log.info("Creating Google Username mapper...");
            
                    IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
                    mapper.setName("Google Username");
                    mapper.setIdentityProviderAlias(Constants.KEYCLOAK_IDP_GOOGLE);
                    mapper.setIdentityProviderMapper(Constants.KEYCLOAK_IDP_MAPPER_OIDC_USER_ATTRIBUTE);

                    Map<String, String> config = new HashMap<>();
                    config.put(Constants.KEYCLOAK_MAPPER_SYNC_MODE, Constants.KEYCLOAK_MAPPER_SYNC_MODE_INHERIT);
                    config.put(Constants.KEYCLOAK_MAPPER_CLAIM, Constants.KEYCLOAK_MAPPER_EMAIL);
                    config.put(Constants.KEYCLOAK_MAPPER_USER_ATTRIBUTE, Constants.KEYCLOAK_MAPPER_USERNAME);
                    mapper.setConfig(config);

                    realm.identityProviders().get(Constants.KEYCLOAK_IDP_GOOGLE).addMapper(mapper);
            log.info("Google Username mapper created successfully");
            
        } catch (Exception e) {
            log.error("Failed to create Google Username mapper: {}", e.getMessage(), e);
        }
    }
    
    private void createGoogleEmailMapper(RealmResource realm) {
        try {
            log.info("Creating Google Email mapper...");
            
                    IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
                    mapper.setName("Google Email");
                    mapper.setIdentityProviderAlias(Constants.KEYCLOAK_IDP_GOOGLE);
                    mapper.setIdentityProviderMapper(Constants.KEYCLOAK_IDP_MAPPER_OIDC_USER_ATTRIBUTE);

                    Map<String, String> config = new HashMap<>();
                    config.put(Constants.KEYCLOAK_MAPPER_SYNC_MODE, Constants.KEYCLOAK_MAPPER_SYNC_MODE_INHERIT);
                    config.put(Constants.KEYCLOAK_MAPPER_CLAIM, Constants.KEYCLOAK_MAPPER_EMAIL);
                    config.put(Constants.KEYCLOAK_MAPPER_USER_ATTRIBUTE, Constants.KEYCLOAK_MAPPER_EMAIL);
                    mapper.setConfig(config);

                    realm.identityProviders().get(Constants.KEYCLOAK_IDP_GOOGLE).addMapper(mapper);
            log.info("Google Email mapper created successfully");
            
        } catch (Exception e) {
            log.error("Failed to create Google Email mapper: {}", e.getMessage(), e);
        }
    }
    
    private void createGoogleFirstNameMapper(RealmResource realm) {
        try {
            log.info("Creating Google First Name mapper...");
            
                    IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
                    mapper.setName("Google First Name");
                    mapper.setIdentityProviderAlias(Constants.KEYCLOAK_IDP_GOOGLE);
                    mapper.setIdentityProviderMapper(Constants.KEYCLOAK_IDP_MAPPER_OIDC_USER_ATTRIBUTE);

                    Map<String, String> config = new HashMap<>();
                    config.put(Constants.KEYCLOAK_MAPPER_SYNC_MODE, Constants.KEYCLOAK_MAPPER_SYNC_MODE_INHERIT);
                    config.put(Constants.KEYCLOAK_MAPPER_CLAIM, Constants.KEYCLOAK_MAPPER_GIVEN_NAME);
                    config.put(Constants.KEYCLOAK_MAPPER_USER_ATTRIBUTE, Constants.KEYCLOAK_MAPPER_FIRST_NAME);
                    mapper.setConfig(config);

                    realm.identityProviders().get(Constants.KEYCLOAK_IDP_GOOGLE).addMapper(mapper);
            log.info("Google First Name mapper created successfully");
            
        } catch (Exception e) {
            log.error("Failed to create Google First Name mapper: {}", e.getMessage(), e);
        }
    }
    
    private void createGoogleLastNameMapper(RealmResource realm) {
        try {
            log.info("Creating Google Last Name mapper...");
            
                    IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
                    mapper.setName("Google Last Name");
                    mapper.setIdentityProviderAlias(Constants.KEYCLOAK_IDP_GOOGLE);
                    mapper.setIdentityProviderMapper(Constants.KEYCLOAK_IDP_MAPPER_HARDCODED_ATTRIBUTE);

                    Map<String, String> config = new HashMap<>();
                    config.put(Constants.KEYCLOAK_MAPPER_SYNC_MODE, Constants.KEYCLOAK_MAPPER_SYNC_MODE_INHERIT);
                    config.put(Constants.KEYCLOAK_MAPPER_ATTRIBUTE_VALUE, Constants.KEYCLOAK_MAPPER_LAST_NAME_DEFAULT);
                    config.put(Constants.KEYCLOAK_MAPPER_ATTRIBUTE, Constants.KEYCLOAK_MAPPER_LAST_NAME);
                    mapper.setConfig(config);

                    realm.identityProviders().get(Constants.KEYCLOAK_IDP_GOOGLE).addMapper(mapper);
            log.info("Google Last Name mapper created successfully");
            
        } catch (Exception e) {
            log.error("Failed to create Google Last Name mapper: {}", e.getMessage(), e);
        }
    }
}
