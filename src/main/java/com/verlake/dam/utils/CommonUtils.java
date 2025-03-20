package com.verlake.dam.utils;


import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CommonUtils {

    private CommonUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    // Generate a random hex string of given length
    public static String generateInviteCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder hexCode = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hexCode.append(Integer.toHexString(random.nextInt(16))); // Generate random hex digit
        }
        return hexCode.toString().toUpperCase(); // Convert to uppercase
    }

    public static ResponseEntity<Map<String, Object>> getSuccessResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(Constants.STATUS_NAME, Constants.STATUS_SUCCESS);
        response.put(Constants.ERROR_MSG_NAME, "");
        return ResponseEntity.ok(response);
    }

    public static String getEmailFromSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            email = jwt.getClaimAsString("email");
        }

        if (email == null || email.isEmpty()) {
            throw new AccessDeniedException("User not authenticated or invalid token");
        }
        return email;
    }

    public static String getKeycloakUserIdFromSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String keycloakUserId = null;

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            keycloakUserId = jwt.getClaimAsString("sub");
        }

        if (keycloakUserId == null || keycloakUserId.isEmpty()) {
            throw new AccessDeniedException("User not authenticated or invalid token");
        }
        return keycloakUserId;
    }

    public static String encrypt(String password, String data) throws EncryptionException,
            NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        // Create a key from the password using PBKDF2
        SecretKeySpec secretKey = generateKeyFromPassword(password);

        // Create AES cipher instance with secure padding
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        // Encrypt the data
        byte[] encryptedData = cipher.doFinal(data.getBytes());

        // Return the encrypted data in Base64 encoding (for safe transmission)
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    // Method to decrypt the string using AES-256
    public static String decrypt(String password, String encryptedData) throws EncryptionException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        // Create a key from the password using PBKDF2
        SecretKeySpec secretKey = generateKeyFromPassword(password);

        // Create AES cipher instance with secure padding
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        // Decode the Base64 encoded data
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);

        // Decrypt the data
        byte[] decryptedData = cipher.doFinal(decodedData);

        // Return the decrypted string
        return new String(decryptedData);
    }

    private static SecretKeySpec generateKeyFromPassword(String password) throws EncryptionException {
        try {
            // Generate a salt (You should store and reuse this salt for decryption)
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            // Create a PBKDF2 key specification
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            // Generate the key
            byte[] key = factory.generateSecret(spec).getEncoded();

            // Return as SecretKeySpec for AES encryption
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new EncryptionException("Error generating key from password", e);
        }
    }

    public static class EncryptionException extends Exception {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
