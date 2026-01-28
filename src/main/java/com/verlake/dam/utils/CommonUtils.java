package com.verlake.dam.utils;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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
        response.put(Constants.STATUS_NAME, Constants.getMessage("status.success"));
        response.put(Constants.ERROR_MESSAGE_NAME, "");
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

    public static String encrypt(String password, String data) throws CryptoException {
        try {
            SecretKeySpec secretKey = generateKeyFromPassword(password);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            // Generate random IV (12 bytes is recommended for GCM)
            byte[] iv = new byte[12];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv); // 128-bit authentication tag

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            byte[] encryptedData = cipher.doFinal(data.getBytes());

            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    // Method to decrypt the string using AES-256
    public static String decrypt(String password, String encryptedData) throws CryptoException {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);

            // Extract IV
            byte[] iv = new byte[12];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

            // Extract encrypted data
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            SecretKeySpec secretKey = generateKeyFromPassword(password);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] decryptedData = cipher.doFinal(encrypted);
            return new String(decryptedData);
        } catch (Exception e) {
            throw new CryptoException("Decryption failed with both legacy and hash methods", e);
        }
    }

    

    private static SecretKeySpec generateKeyFromPassword(String password) {
        byte[] key = new byte[16];
        byte[] passwordBytes = password.getBytes();
        System.arraycopy(passwordBytes, 0, key, 0, Math.min(passwordBytes.length, 16));
        return new SecretKeySpec(key, "AES");
    }

    /**
     * Custom exception for cryptographic operations
     */
    public static class CryptoException extends Exception {
        public CryptoException(String message) {
            super(message);
        }

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class EncryptionException extends Exception {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ===== SECURE VALIDATION METHODS (ReDoS Protected) =====

    /**
     * Validate password strength without vulnerable regex patterns
     * Uses character-by-character checking to prevent ReDoS attacks
     */
    public static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpperCase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowerCase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (isSpecialCharacter(c)) {
                hasSpecialChar = true;
            }
        }

        if (!hasUpperCase) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter (A-Z)");
        }
        if (!hasLowerCase) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter (a-z)");
        }
        if (!hasDigit) {
            throw new IllegalArgumentException("Password must contain at least one digit (0-9)");
        }
        if (!hasSpecialChar) {
            throw new IllegalArgumentException("Password must contain at least one special character (!@#$%^&*()-_=+[]{}|;:'\",.<>/?)");
        }
    }

    /**
     * Check if character is a special character (ReDoS safe)
     */
    private static boolean isSpecialCharacter(char c) {
        return "!@#$%^&*()-_=+[]{}|;:'\",.<>/?".indexOf(c) != -1;
    }

    /**
     * Validate SQL identifier without vulnerable regex patterns
     * Uses character-by-character checking to prevent ReDoS attacks
     */
    public static boolean isValidSqlIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }

        String trimmed = identifier.trim();
        
        // Check length limit to prevent DoS
        if (trimmed.length() > 128) {
            return false;
        }

        // Check each character individually
        for (char c : trimmed.toCharArray()) {
            if (!isValidSqlIdentifierChar(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if character is valid for SQL identifier (ReDoS safe)
     */
    private static boolean isValidSqlIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    /**
     * Validate username without vulnerable regex patterns
     * Uses character-by-character checking to prevent ReDoS attacks
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String trimmed = username.trim();
        
        // Check length limit to prevent DoS
        if (trimmed.length() > 64) {
            return false;
        }

        // Check each character individually
        for (char c : trimmed.toCharArray()) {
            if (!isValidUsernameChar(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if character is valid for username (ReDoS safe)
     */
    private static boolean isValidUsernameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-';
    }

    /**
     * Escape SQL identifier to prevent injection (ReDoS safe)
     */
    public static String escapeSqlIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }

        // Use StringBuilder for efficient string manipulation
        StringBuilder escaped = new StringBuilder();
        for (char c : identifier.toCharArray()) {
            switch (c) {
                case '`' -> escaped.append("``");  // MySQL backtick escape
                case '"' -> escaped.append("\"\""); // PostgreSQL/SQL Server quote escape
                case '[' -> escaped.append("[[");  // SQL Server bracket escape
                case ']' -> escaped.append("]]");  // SQL Server bracket escape
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * Escape PostgreSQL identifier specifically
     */
    public static String escapePostgresqlIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : identifier.toCharArray()) {
            if (c == '"') {
                escaped.append("\"\"");
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * Escape SQL Server identifier specifically
     */
    public static String escapeSqlServerIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : identifier.toCharArray()) {
            if (c == '[' || c == ']') {
                escaped.append(c).append(c); // Double the brackets
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

}
