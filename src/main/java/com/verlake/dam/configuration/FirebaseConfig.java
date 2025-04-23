package com.verlake.dam.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.verlake.dam.exception.FirebaseConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${FIREBASE_PRIVATE_KEY}")
    private String privateKey;

    @Value("${FIREBASE_CLIENT_EMAIL}")
    private String clientEmail;

    @Value("${FIREBASE_PROJECT_ID}")
    private String projectId;

    @Value("${FIREBASE_PRIVATE_KEY_ID}")
    private String privateKeyId;

    @Value("${FIREBASE_CLIENT_X509_CERT_URL}")
    private String clientX509CertUrl;

    @Value("${FIREBASE_CLIENT_ID}")
    private String clientId;

    @Value("${firebase.config.path:firebase/firebase-config.json}")
    private String firebaseConfigPath;

    @PostConstruct
    public void initialize() {
        try {
            Resource resource = new ClassPathResource(firebaseConfigPath);
            if (!resource.exists()) {
                throw new FirebaseConfigurationException(
                    String.format("Firebase configuration file not found at: %s", firebaseConfigPath)
                );
            }

            try (InputStream inputStream = resource.getInputStream()) {
                String template = FileCopyUtils.copyToString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                
                // Format the template with the actual values
                String jsonCredentials = String.format(template,
                    projectId,
                    privateKeyId,
                    privateKey,
                    clientEmail,
                    clientId,
                    clientX509CertUrl
                );

                try (InputStream credentialsStream = new java.io.ByteArrayInputStream(
                    jsonCredentials.getBytes(StandardCharsets.UTF_8))) {
                    
                    FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                        .build();

                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options);
                        log.info("Firebase application initialized successfully");
                    }
                }
            }
        } catch (IOException e) {
            String errorMessage = "Failed to initialize Firebase due to I/O error";
            log.error(errorMessage, e);
            throw new FirebaseConfigurationException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage = "Failed to initialize Firebase due to unexpected error";
            log.error(errorMessage, e);
            throw new FirebaseConfigurationException(errorMessage, e);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }
}