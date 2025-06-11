package com.verlake.dam.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.Email;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.exception.EmailSendingException;
import com.verlake.dam.repository.EmailRepository;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.email.EmailException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.DeleteQueryAlertData;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender emailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private UserRepository userRepository;

    private final String hostDomainUri;

    private final String mailSender;

    public EmailService(@Value("${HOST_DOMAIN_URI}") String hostDomainUri, @Value("${MAIL_SENDER}") String mailSender) {
        this.hostDomainUri = hostDomainUri;
        this.mailSender = mailSender;
    }

    private Email createEmailEntity(User user,
            EmailType emailType,
            String emailSubject,
            ObjectNode metaData,
            String htmlContent) {
        // Create Email entity and store in the database
        Email email = new Email();
        email.setEmailTo(user.getEmail());
        email.setEmailType(emailType); // Use the enum for email type
        email.setSubject(emailSubject);
        email.setMetadata(metaData);
        email.setSentAt(LocalDateTime.now());
        emailRepository.save(email); // Save email record in the database

        metaData.put("mailContent", htmlContent);
        email.setMetadata(metaData);
        emailRepository.save(email);
        return email;
    }

    private void sendEmail(String emailAddress, String emailSubject, String htmlContent) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(mailSender);
        helper.setTo(emailAddress);
        helper.setSubject(emailSubject);
        helper.setText(htmlContent, true); // true indicates HTML content

        // Send the email
        emailSender.send(message);
    }

    public void sendInvitationEmail(User user, String emailTmplFile) {
        // Prepare Thymeleaf context for email content
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_USER_NAME, user.getFirstName() + " " + user.getLastName());
        context.setVariable(Constants.EMAIL_VAR_TEMP_PASSWORD, user.getPassword());

        // Generate email content using Thymeleaf template
        String inviteCode = CommonUtils.generateInviteCode(Constants.INVITE_CODE_LENGTH);
        String redirectLink = hostDomainUri + "?inviteCode=" + inviteCode;
        context.setVariable(Constants.EMAIL_VAR_REDIRECT_LINK, redirectLink);
        
        // Add password guidelines to email context
        context.setVariable("passwordGuidelines", getPasswordGuidelines());

        String emailSubject = "Invitation to Join Our DAM System";
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_INVITE_CODE, inviteCode);
        metaData.put(Constants.EMAIL_VAR_REDIRECT_LINK, redirectLink);

        String htmlContent = templateEngine.process(emailTmplFile, context);

        // Create Email entity and store in the database
        Email email = createEmailEntity(user, EmailType.INVITATION, emailSubject, metaData, htmlContent);

        try {
            sendEmail(user.getEmail(), emailSubject, htmlContent);
        } catch (Exception e) {
            emailRepository.delete(email);
            userRepository.delete(user);
            log.error("Failed to send invitation email to {} ", user.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send invitation email to " + user.getEmail());
        }
    }

    /**
     * Returns password complexity guidelines as a formatted HTML string for emails
     */
    private String getPasswordGuidelines() {
        return "<strong>Password Requirements:</strong><br/>" +
               "• Minimum 12 characters<br/>" +
               "• At least one uppercase letter (A–Z)<br/>" +
               "• At least one lowercase letter (a–z)<br/>" +
               "• At least one digit (0–9)<br/>" +
               "• At least one special character (!@#$%^&*()-_=+[]{}|;:'\",.<>/?)";
    }

    public void sendAssetRelinquishEmail(User admin, User owner, AssetCredential assetCredential,
            String emailTmplFile) {
        // Prepare Thymeleaf context for email content
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_ADMIN_NAME, admin.getFirstName() + " " + admin.getLastName());
        context.setVariable(Constants.EMAIL_VAR_OWNER_NAME, owner.getFirstName() + " " + owner.getLastName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, assetCredential.getAsset().getName());

        String emailSubject = "Relinquish Asset";
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_ASSET_NAME, assetCredential.getAsset().getName());
        metaData.put(Constants.EMAIL_VAR_ASSET_CREDENTIAL_ID, assetCredential.getId());

        String htmlContent = templateEngine.process(emailTmplFile, context);
        metaData.put("mailContent", htmlContent);
        createEmailEntity(admin, EmailType.RELINQUISH_ASSET_CREDENTIAL, emailSubject, metaData, emailTmplFile);

        try {
            sendEmail(admin.getEmail(), emailSubject, htmlContent);
        } catch (Exception e) {
            log.error("Failed to send relinquish asset email to {} ", admin.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send invitation email to " + admin.getEmail());
        }
    }

    public void sendAssetApproveNotifyEmail(Asset asset, User approver, String method, String emailTmplFile) {
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        context.setVariable(Constants.EMAIL_VAR_APPROVER_NAME, approver.getFirstName() + " " + approver.getLastName());
        context.setVariable(Constants.EMAIL_VAR_METHOD,
                method.equals(Constants.ASSET_ADD_NAME) ? "added to" : "removed from");

        String emailSubject = "Asset Approve Notification";
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        metaData.put(Constants.EMAIL_VAR_APPROVER_NAME, approver.getFirstName() + " " + approver.getLastName());
        metaData.put(Constants.EMAIL_VAR_METHOD, method.equals(Constants.ASSET_ADD_NAME) ? "added to" : "removed from");

        String emailContent = templateEngine.process(emailTmplFile, context);
        createEmailEntity(approver, EmailType.ASSET_APPROVE_NOTIFY, emailSubject, metaData, emailContent);

        try {
            sendEmail(approver.getEmail(), emailSubject, emailContent);
        } catch (Exception e) {
            log.error("Failed to send assigning asset approver email to {} ", approver.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send Asset Approver email to " + approver.getEmail());
        }
    }

    private void sendAssetRequestEmail(User receiver, User sender, Asset asset, String emailTmplFile,
            ApprovalStatus approvalStatus, EmailType emailType, Map<String, String> newCredMapper) {

        String emailSubject = getEmailSubject(emailType);
        String errorMessage = getErrorMessage(emailType);

        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_RECEIVER_FIRST_NAME, receiver.getFirstName());
        context.setVariable(Constants.EMAIL_VAR_RECEIVER_LAST_NAME, receiver.getLastName());
        if (emailType == EmailType.DEVELOPER_ASSET_REQUEST_NOTIFY || emailType == EmailType.DEVELOPER_RELINQUISH_ASSET_NOTIFY) {
            context.setVariable(Constants.EMAIL_VAR_REQUESTOR_FIRST_NAME, sender.getFirstName());
            context.setVariable(Constants.EMAIL_VAR_REQUESTOR_LAST_NAME, sender.getLastName());
        } else if (emailType == EmailType.APPROVAL_ASSET_ACCESS_REQUEST) {
            context.setVariable(Constants.EMAIL_VAR_APPROVER_FIRST_NAME, sender.getFirstName());
            context.setVariable(Constants.EMAIL_VAR_APPROVER_LAST_NAME, sender.getLastName());
            context.setVariable(Constants.EMAIL_VAR_APPROVAL_STATUS, approvalStatus.getDisplayName());

            if (newCredMapper != null && !newCredMapper.isEmpty()) {
                context.setVariable(Constants.EMAIL_VAR_DB_USERNAME,
                        newCredMapper.get(Constants.EMAIL_VAR_DB_USERNAME));
                context.setVariable(Constants.EMAIL_VAR_DB_PASSWORD,
                        newCredMapper.get(Constants.EMAIL_VAR_DB_PASSWORD));
            }
        }

        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        metaData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());

        String emailContent = templateEngine.process(emailTmplFile, context);

        createEmailEntity(receiver, emailType, emailSubject, metaData, emailContent);
        try {
            sendEmail(receiver.getEmail(), emailSubject, emailContent);
        } catch (MessagingException e) {
            log.error(String.format(errorMessage, receiver.getEmail()), e);
            throw new EmailSendingException(HttpStatus.INTERNAL_SERVER_ERROR,
                    String.format(errorMessage, receiver.getEmail()), e);
        }
    }

    public void sendDeveloperAssetRequestEmail(User receiver, User requestor, Asset asset, String emailTmplFile) {
        HashMap<String, String> newCredMapper = new HashMap<>();
        sendAssetRequestEmail(
                receiver,
                requestor,
                asset,
                emailTmplFile,
                ApprovalStatus.PENDING,
                EmailType.DEVELOPER_ASSET_REQUEST_NOTIFY,
                newCredMapper);
    }

    public void sendDeveloperRelinquishEmail(User receiver, User requestor, Asset asset, String emailTmplFile) {
        HashMap<String, String> newCredMapper = new HashMap<>();
        sendAssetRequestEmail(
                receiver,
                requestor,
                asset,
                emailTmplFile,
                ApprovalStatus.PENDING,
                EmailType.DEVELOPER_RELINQUISH_ASSET_NOTIFY,
                newCredMapper);
    }

    public void sendApprovalAssetAccessRequestEmail(User developer,
            User approver,
            Asset asset,
            String emailTmplFile,
            ApprovalStatus approvalStatus,
            Map<String, String> newCredMapper) {
        sendAssetRequestEmail(
                developer,
                approver,
                asset,
                emailTmplFile,
                approvalStatus,
                EmailType.APPROVAL_ASSET_ACCESS_REQUEST,
                newCredMapper);
    }

    public void sendDeleteQueryAlertEmail(User assetOwner, Asset asset, DeleteQueryAlertData alertData, String emailTmplFile) {
        
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_OWNER_NAME, assetOwner.getFirstName() + " " + assetOwner.getLastName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        context.setVariable(Constants.EMAIL_VAR_DATABASE_TYPE, alertData.getDatabaseType());
        context.setVariable(Constants.EMAIL_VAR_HOST_URL, alertData.getHostUrl());
        context.setVariable(Constants.EMAIL_VAR_EXECUTOR_NAME, alertData.getExecutorName());
        context.setVariable(Constants.EMAIL_VAR_EXECUTION_TIME, alertData.getExecutionTime());
        context.setVariable(Constants.EMAIL_VAR_TABLE_NAME, alertData.getTableName());
        context.setVariable(Constants.EMAIL_VAR_AFFECTED_ROWS, alertData.getAffectedRows());
        context.setVariable(Constants.EMAIL_VAR_QUERY, alertData.getQuery());

        String emailSubject = "DELETE Query Alert - " + asset.getName();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        metaData.put(Constants.EMAIL_VAR_DATABASE_TYPE, alertData.getDatabaseType());
        metaData.put(Constants.EMAIL_VAR_HOST_URL, alertData.getHostUrl());
        metaData.put(Constants.EMAIL_VAR_EXECUTOR_NAME, alertData.getExecutorName());
        metaData.put(Constants.EMAIL_VAR_EXECUTION_TIME, alertData.getExecutionTime());
        metaData.put(Constants.EMAIL_VAR_TABLE_NAME, alertData.getTableName());
        metaData.put(Constants.EMAIL_VAR_AFFECTED_ROWS, alertData.getAffectedRows());
        metaData.put(Constants.EMAIL_VAR_QUERY, alertData.getQuery());

        String emailContent = templateEngine.process(emailTmplFile, context);
        createEmailEntity(assetOwner, EmailType.DELETE_QUERY_ALERT, emailSubject, metaData, emailContent);

        try {
            sendEmail(assetOwner.getEmail(), emailSubject, emailContent);
            log.info("DELETE query alert email sent to asset owner: {}", assetOwner.getEmail());
        } catch (Exception e) {
            log.error("Failed to send DELETE query alert email to {} ", assetOwner.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send DELETE query alert email to " + assetOwner.getEmail());
        }
    }

    private String getEmailSubject(EmailType emailType) {
        switch (emailType) {
            case DEVELOPER_ASSET_REQUEST_NOTIFY:
                return "Developer Asset Access Request";
            case APPROVAL_ASSET_ACCESS_REQUEST:
                return "Approval Asset Access Request";
            case ASSET_QUERY_CHANGE_REQUEST_APPROVAL_NOTIFY:
                return "Asset Query Change Request Approval";
            case DELETE_QUERY_ALERT:
                return "DELETE Query Security Alert";
            default:
                return "Asset Access Request";
        }
    }

    private String getErrorMessage(EmailType emailType) {
        switch (emailType) {
            case DEVELOPER_ASSET_REQUEST_NOTIFY:
                return "Failed to send developer asset request email to %s";
            case APPROVAL_ASSET_ACCESS_REQUEST:
                return "Failed to send approval asset access request email to %s";
            case ASSET_QUERY_CHANGE_REQUEST_NOTIFY:
                return "Failed to send asset query change request notification email to %s";
            case ASSET_QUERY_CHANGE_REQUEST_APPROVAL_NOTIFY:
                return "Failed to send asset query change request approval email to %s";
            case DELETE_QUERY_ALERT:
                return "Failed to send DELETE query alert email to %s";
            default:
                return "Failed to send asset request email to %s";
        }
    }

    public void sendAssetQueryChangeRequestEmail(User receiver, User requestor, Asset asset, 
                                               String ticketReference, String changeDescription, 
                                               String query, String emailTmplFile) {
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_RECEIVER_FIRST_NAME, receiver.getFirstName());
        context.setVariable(Constants.EMAIL_VAR_RECEIVER_LAST_NAME, receiver.getLastName());
        context.setVariable(Constants.EMAIL_VAR_REQUESTOR_FIRST_NAME, requestor.getFirstName());
        context.setVariable(Constants.EMAIL_VAR_REQUESTOR_LAST_NAME, requestor.getLastName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());
        context.setVariable(Constants.EMAIL_VAR_TICKET_REFERENCE, ticketReference);
        context.setVariable(Constants.EMAIL_VAR_CHANGE_DESCRIPTION, changeDescription);
        context.setVariable(Constants.EMAIL_VAR_QUERY, query);

        String emailSubject = "Asset Query Change Request Notification";
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        metaData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());
        metaData.put(Constants.EMAIL_VAR_REQUESTOR_FIRST_NAME, requestor.getFirstName());
        metaData.put(Constants.EMAIL_VAR_REQUESTOR_LAST_NAME, requestor.getLastName());
        metaData.put(Constants.EMAIL_VAR_TICKET_REFERENCE, ticketReference != null ? ticketReference : "");
        metaData.put(Constants.EMAIL_VAR_CHANGE_DESCRIPTION, changeDescription != null ? changeDescription : "");
        metaData.put(Constants.EMAIL_VAR_QUERY, query != null ? query : "");

        String emailContent = templateEngine.process(emailTmplFile, context);
        createEmailEntity(receiver, EmailType.ASSET_QUERY_CHANGE_REQUEST_NOTIFY, emailSubject, metaData, emailContent);

        try {
            sendEmail(receiver.getEmail(), emailSubject, emailContent);
        } catch (MessagingException e) {
            log.error("Failed to send asset query change request notification email to {}", receiver.getEmail(), e);
            throw new EmailSendingException(HttpStatus.INTERNAL_SERVER_ERROR,
                    String.format("Failed to send asset query change request notification email to %s", receiver.getEmail()), e);
        }
    }

    public void sendAssetQueryChangeRequestApprovalEmail(User requestor, User approver, Asset asset, 
                                                        AccessQueryDTO queryDTO, String emailTemplate) {
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_REQUESTOR_FIRST_NAME, requestor.getFirstName());
        context.setVariable(Constants.EMAIL_VAR_REQUESTOR_LAST_NAME, requestor.getLastName());
        context.setVariable(Constants.EMAIL_VAR_APPROVER_FIRST_NAME, approver.getFirstName());
        context.setVariable(Constants.EMAIL_VAR_APPROVER_LAST_NAME, approver.getLastName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());
        context.setVariable(Constants.EMAIL_VAR_TICKET_REFERENCE, queryDTO.getTicketReference() != null ? queryDTO.getTicketReference() : "");
        context.setVariable(Constants.EMAIL_VAR_CHANGE_DESCRIPTION, queryDTO.getChangeDescription() != null ? queryDTO.getChangeDescription() : "");
        context.setVariable(Constants.EMAIL_VAR_QUERY, queryDTO.getQuery() != null ? queryDTO.getQuery() : "");
        context.setVariable(Constants.EMAIL_VAR_REJECT_REASON, queryDTO.getRejectReason() != null ? queryDTO.getRejectReason() : "");
        context.setVariable(Constants.EMAIL_VAR_APPROVAL_STATUS, queryDTO.getApprovalStatus().name());

        String emailSubject = String.format("Query Change Request %s - %s", 
                queryDTO.getApprovalStatus().getDisplayName(), asset.getName());
        
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        metaData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());
        metaData.put(Constants.EMAIL_VAR_REQUESTOR_FIRST_NAME, requestor.getFirstName());
        metaData.put(Constants.EMAIL_VAR_REQUESTOR_LAST_NAME, requestor.getLastName());
        metaData.put(Constants.EMAIL_VAR_APPROVER_FIRST_NAME, approver.getFirstName());
        metaData.put(Constants.EMAIL_VAR_APPROVER_LAST_NAME, approver.getLastName());
        metaData.put(Constants.EMAIL_VAR_TICKET_REFERENCE, queryDTO.getTicketReference() != null ? queryDTO.getTicketReference() : "");
        metaData.put(Constants.EMAIL_VAR_CHANGE_DESCRIPTION, queryDTO.getChangeDescription() != null ? queryDTO.getChangeDescription() : "");
        metaData.put(Constants.EMAIL_VAR_QUERY, queryDTO.getQuery() != null ? queryDTO.getQuery() : "");
        metaData.put(Constants.EMAIL_VAR_REJECT_REASON, queryDTO.getRejectReason() != null ? queryDTO.getRejectReason() : "");
        metaData.put(Constants.EMAIL_VAR_APPROVAL_STATUS, queryDTO.getApprovalStatus().name());

        String emailContent = templateEngine.process(emailTemplate, context);
        createEmailEntity(requestor, EmailType.ASSET_QUERY_CHANGE_REQUEST_APPROVAL_NOTIFY, emailSubject, metaData, emailContent);

        try {
            sendEmail(requestor.getEmail(), emailSubject, emailContent);
            log.info("Asset query change request approval email sent to: {}", requestor.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send asset query change request approval email to {}", requestor.getEmail(), e);
            throw new EmailSendingException(HttpStatus.INTERNAL_SERVER_ERROR,
                    String.format("Failed to send asset query change request approval email to %s", requestor.getEmail()), e);
        }
    }

}
