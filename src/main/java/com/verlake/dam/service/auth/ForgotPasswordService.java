package com.verlake.dam.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.exception.JwtTokenException;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@Slf4j
public class ForgotPasswordService {

    @Autowired
    private UserService userService;

    @Autowired
    private KeycloakService keycloakService;

    @Autowired
    private NotificationTaskRepository notificationTaskRepository;

    @Autowired
    private JwtPasswordResetService jwtPasswordResetService;

    @Value("${auth.provider}")
    private String authProvider;

    @Value("${HOST_DOMAIN_URI}")
    private String hostDomainUri;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Initiates forgot password process by generating a JWT token and sending email
     */
    public void initiateForgotPassword(String email) {
        log.info("Initiating forgot password process for email: {}", email);

        // Check if user exists
        User user = userService.findByEmail(email);
        if (user == null) {
            log.warn("Forgot password request for non-existent email: {}", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Email not found in our system");
        }

        // Generate JWT token
        String jwtToken = jwtPasswordResetService.generatePasswordResetToken(user);
        log.info("JWT password reset token generated for user: {} (email: {})", user.getId(), email);

        // Generate reset link with JWT token
        String resetLink = hostDomainUri + "/auth/reset-password?token=" + jwtToken;

        // Create notification task for email sending
        try {
            createForgotPasswordNotificationTask(user, resetLink);
            log.info("Forgot password notification task created successfully for: {}", email);
        } catch (Exception e) {
            log.error("Failed to create forgot password notification task for: {}", email, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to create password reset notification. Please try again later.");
        }
    }

    /**
     * Validates a password reset JWT token
     */
    public User validateResetToken(String token) {
        log.info("Validating JWT reset token");

        try {
            Map<String, Object> tokenData = jwtPasswordResetService.validatePasswordResetToken(token);
            String email = (String) tokenData.get(Constants.JWT_CLAIM_EMAIL);
            
            // Get user
            User user = userService.findByEmail(email);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found");
            }

            log.info("JWT reset token validated successfully for user: {}", user.getEmail());
            return user;
            
        } catch (JwtTokenException e) {
            log.warn("Invalid JWT reset token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error validating JWT reset token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, Constants.JWT_ERROR_INVALID_TOKEN);
        }
    }

    /**
     * Updates password using a valid JWT reset token
     */
    public void updatePasswordFromToken(String token, String newPassword) {
        log.info("Updating password using JWT token");

        // Validate token and get user
        User user = validateResetToken(token);

        // Validate password complexity
        userService.checkPasswordComplexity(newPassword);

        // Update password in the system
        if (authProvider.contains(AuthProvider.KEYCLOAK.toString().toLowerCase())) {
            keycloakService.saveUser(user.getEmail(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    newPassword, false);
        }

        log.info("Password updated successfully for user: {}", user.getEmail());
    }

    /**
     * Creates a notification task for forgot password email
     */
    private void createForgotPasswordNotificationTask(User user, String resetLink) throws JsonProcessingException {
        // Create notification data with reset link
        ObjectNode notificationData = objectMapper.createObjectNode();
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.put("resetLink", resetLink);
        notificationData.set(Constants.ACCESS_OBJECT_ATTR_DATA, dataNode);

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(user);
        task.setSender(null);
        task.setAsset(null);
        task.setNotificationMessage(objectMapper.writeValueAsString(notificationData));
        task.setEmailType(EmailType.FORGOT_PASSWORD);
        task.setSent(false);
        
        notificationTaskRepository.save(task);
        log.info("Created forgot password notification task for user: {}", user.getEmail());
    }
} 