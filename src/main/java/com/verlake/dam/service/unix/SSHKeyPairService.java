package com.verlake.dam.service.unix;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.verlake.dam.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * Service for generating and managing SSH key pairs
 */
@Service
@Slf4j
public class SSHKeyPairService {
    
    private static final int RSA_KEY_SIZE = 2048;
    
    /**
     * Result object containing generated key pair
     */
    public static class SSHKeyPairResult {
        private final String publicKey;
        private final String privateKey;
        private final String fingerprint;
        
        public SSHKeyPairResult(String publicKey, String privateKey, String fingerprint) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
            this.fingerprint = fingerprint;
        }
        
        public String getPublicKey() {
            return publicKey;
        }
        
        public String getPrivateKey() {
            return privateKey;
        }
        
        public String getFingerprint() {
            return fingerprint;
        }
    }
    
    /**
     * Generate a new SSH key pair (RSA 2048-bit)
     * 
     * @return SSHKeyPairResult containing public key, private key, and fingerprint
     * @throws Exception if key generation fails
     */
    public SSHKeyPairResult generateKeyPair() throws Exception {
        log.info("Generating new SSH key pair (RSA {})", RSA_KEY_SIZE);
        
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, RSA_KEY_SIZE);
        
        // Generate private key
        ByteArrayOutputStream privateKeyOutputStream = new ByteArrayOutputStream();
        keyPair.writePrivateKey(privateKeyOutputStream);
        String privateKey = privateKeyOutputStream.toString();
        
        // Generate public key in OpenSSH format
        ByteArrayOutputStream publicKeyOutputStream = new ByteArrayOutputStream();
        keyPair.writePublicKey(publicKeyOutputStream, "");
        String publicKey = publicKeyOutputStream.toString().trim();
        
        // Generate fingerprint
        String fingerprint = keyPair.getFingerPrint();
        
        // Clean up
        keyPair.dispose();
        
        log.info("SSH key pair generated successfully. Fingerprint: {}", fingerprint);
        
        return new SSHKeyPairResult(publicKey, privateKey, fingerprint);
    }
    
    /**
     * Encrypt a private key using the user's Keycloak key
     * 
     * @param privateKey The private key to encrypt
     * @param userKey The user's Keycloak encryption key
     * @return Encrypted private key
     * @throws CommonUtils.CryptoException if encryption fails
     */
    public String encryptPrivateKey(String privateKey, String userKey) throws CommonUtils.CryptoException {
        if (userKey == null || userKey.isEmpty()) {
            throw new IllegalArgumentException("User encryption key is required");
        }
        
        log.debug("Encrypting private key using user's encryption key");
        String encrypted = CommonUtils.encrypt(userKey, privateKey);
        log.debug("Private key encrypted successfully, length: {} chars", encrypted.length());
        
        return encrypted;
    }
    
    /**
     * Decrypt a private key using the user's Keycloak key
     * 
     * @param encryptedPrivateKey The encrypted private key
     * @param userKey The user's Keycloak encryption key
     * @return Decrypted private key
     * @throws CommonUtils.CryptoException if decryption fails
     */
    public String decryptPrivateKey(String encryptedPrivateKey, String userKey) throws CommonUtils.CryptoException {
        if (userKey == null || userKey.isEmpty()) {
            throw new IllegalArgumentException("User encryption key is required");
        }
        
        log.debug("Decrypting private key using user's encryption key");
        String decrypted = CommonUtils.decrypt(userKey, encryptedPrivateKey);
        log.debug("Private key decrypted successfully");
        
        return decrypted;
    }
    
    /**
     * Format public key for authorized_keys file
     * 
     * @param publicKey The public key
     * @param comment Optional comment (e.g., username or email)
     * @return Formatted public key
     */
    public String formatPublicKeyForAuthorizedKeys(String publicKey, String comment) {
        String formattedKey = publicKey.trim();
        
        if (comment != null && !comment.isEmpty()) {
            // SSH public keys have format: <key-type> <key-data> [optional-comment]
            // Split by spaces to separate the parts
            String[] parts = formattedKey.split("\\s+", 3);
            
            // Only keep key-type and key-data (first 2 parts), remove existing comment if present
            if (parts.length >= 2) {
                formattedKey = parts[0] + " " + parts[1];
            }
            
            // Add the new comment
            formattedKey = formattedKey + " " + comment;
        }
        
        return formattedKey;
    }
    
    /**
     * Validate that a string is a valid SSH public key
     * 
     * @param publicKey The public key to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidPublicKey(String publicKey) {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = publicKey.trim();
        
        // Check if it starts with a valid key type
        return trimmed.startsWith("ssh-rsa ") || 
               trimmed.startsWith("ssh-dss ") || 
               trimmed.startsWith("ecdsa-") || 
               trimmed.startsWith("ssh-ed25519 ");
    }
}

