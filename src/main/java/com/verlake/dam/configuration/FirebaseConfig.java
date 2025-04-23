package com.verlake.dam.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        // Create a JSON string with the credentials
        String jsonCredentials = String.format(
            "{\"type\":\"service_account\",\"project_id\":\"%s\",\"private_key_id\":\"%s\",\"private_key\":\"%s\",\"client_email\":\"%s\",\"client_id\":\"%s\",\"auth_uri\":\"https://accounts.google.com/o/oauth2/auth\",\"token_uri\":\"https://oauth2.googleapis.com/token\",\"auth_provider_x509_cert_url\":\"https://www.googleapis.com/oauth2/v1/certs\",\"client_x509_cert_url\":\"%s\"}",
            projectId,
            privateKeyId,
            privateKey,
            clientEmail,
            clientId,
            clientX509CertUrl
        );

        // Convert JSON string to InputStream
        InputStream credentialsStream = new java.io.ByteArrayInputStream(jsonCredentials.getBytes(StandardCharsets.UTF_8));
        
        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
        
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();
                
        return FirebaseApp.initializeApp(options);
    }
}