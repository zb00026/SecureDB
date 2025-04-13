package com.verlake.dam.utils;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.util.Arrays;
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

    public static String encrypt(String password, String data) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        SecretKeySpec secretKey = generateKeyFromPassword(password);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");

        // Generate random IV
        byte[] iv = new byte[16];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        byte[] encryptedData = cipher.doFinal(data.getBytes());

        // Combine IV and encrypted data
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

        return Base64.getEncoder().encodeToString(combined);

    }

    // Method to decrypt the string using AES-256
    public static String decrypt(String password, String encryptedData) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        byte[] combined = Base64.getDecoder().decode(encryptedData);

        // Extract IV
        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // Extract encrypted data
        byte[] encrypted = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

        SecretKeySpec secretKey = generateKeyFromPassword(password);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

        byte[] decryptedData = cipher.doFinal(encrypted);
        return new String(decryptedData);
    }

    private static SecretKeySpec generateKeyFromPassword(String password) {
        byte[] key = new byte[16];
        byte[] passwordBytes = password.getBytes();
        System.arraycopy(passwordBytes, 0, key, 0, Math.min(passwordBytes.length, 16));
        return new SecretKeySpec(key, "AES");
    }

    public static class EncryptionException extends Exception {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
