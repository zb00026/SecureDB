package com.verlake.dam.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.Email;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.repository.EmailRepository;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;

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
        email.setEmailType(emailType);  // Use the enum for email type
        email.setSubject(emailSubject);
        email.setMetadata(metaData);
        email.setSentAt(LocalDateTime.now());
        emailRepository.save(email);  // Save email record in the database

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
        helper.setText(htmlContent, true);  // true indicates HTML content

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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send invitation email to " + user.getEmail());
        }
    }

    public void sendAssetRelinquishEmail(User admin, User owner, AssetCredential assetCredential, String emailTmplFile) {
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send invitation email to " + admin.getEmail());
        }
    }

    public void sendAssetApproveNotifyEmail(Asset asset, User approver, String method, String emailTmplFile) {
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        context.setVariable(Constants.EMAIL_VAR_APPROVER_NAME, approver.getFirstName() + " " + approver.getLastName());
        context.setVariable(Constants.EMAIL_VAR_METHOD, method.equals(Constants.ASSET_ADD_NAME) ? "added to" : "removed from");
        
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send Asset Approver email to " + approver.getEmail());
        }
    }

    public void sendDeveloperAssetRequestEmail(User receiver, User requestor, Asset asset, String emailTmplFile) {
        Context context = new Context();
        context.setVariable(Constants.EMAIL_VAR_RECEIVER_FIRST_NAME, receiver.getFirstName());
        context.setVariable(Constants.EMAIL_VAR_RECEIVER_LAST_NAME, receiver.getLastName());
        context.setVariable(Constants.EMAIL_VAR_REQUESTOR_FIRST_NAME, requestor.getFirstName());
        context.setVariable(Constants.EMAIL_VAR_REQUESTOR_LAST_NAME, requestor.getLastName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        context.setVariable(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());

        String emailSubject = "Developer Asset Access Request";
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        metaData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());

        String emailContent = templateEngine.process(emailTmplFile, context);

        createEmailEntity(receiver, EmailType.DEVELOPER_ASSET_REQUEST_NOTIFY, emailSubject, metaData, emailContent);

        try {
            sendEmail(receiver.getEmail(), emailSubject, emailContent);
        } catch (Exception e) {
            log.error("Failed to send developer asset request email to {} ", receiver.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send developer asset request email to " + receiver.getEmail());
        }
    }
}
