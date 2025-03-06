package com.verlake.dam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verlake.dam.entity.Email;
import com.verlake.dam.entity.User;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.repository.EmailRepository;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
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

    public void sendInvitationEmail(User user, String emailTmplFile) {
        // Prepare Thymeleaf context for email content
        Context context = new Context();
        context.setVariable("userName", user.getFirstName() + " " + user.getLastName());
        context.setVariable("tempPassword", user.getPassword());

        // Generate email content using Thymeleaf template
        String inviteCode = CommonUtils.generateInviteCode(Constants.INVITE_CODE_LENGTH);
        String redirectLink = hostDomainUri + "?inviteCode=" + inviteCode;
        context.setVariable("redirectLink", redirectLink);

        String emailSubject = "Invitation to Join Our DAM System";
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode metaData = objectMapper.createObjectNode();
        metaData.put("inviteCode", inviteCode);
        metaData.put("redirectLink", redirectLink);
        // Create Email entity and store in the database
        Email email = new Email();
        email.setEmailTo(user.getEmail());
        email.setEmailType(EmailType.INVITATION);  // Use the enum for email type
        email.setSubject(emailSubject);
        email.setMetadata(metaData);
        email.setSentAt(LocalDateTime.now());
        emailRepository.save(email);  // Save email record in the database

        String htmlContent = templateEngine.process(emailTmplFile, context);
        metaData.put("mailContent", htmlContent);
        email.setMetadata(metaData);
        emailRepository.save(email);

        // Create MimeMessage to send email
        MimeMessage message = emailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(mailSender);
            helper.setTo(user.getEmail());
            helper.setSubject(emailSubject);
            helper.setText(htmlContent, true);  // true indicates HTML content

            // Send the email
            emailSender.send(message);
        } catch (Exception e) {
            emailRepository.delete(email);
            userRepository.delete(user);
            log.error("Failed to send invitation email to {} : {}", user.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send invitation email to " + user.getEmail());
        }

    }
}
