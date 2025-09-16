package com.verlake.dam.service.terminal;

import com.jcraft.jsch.*;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.verlake.dam.utils.Constants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Service for managing SSH connections and operations
 * Handles the actual SSH protocol communication using JSch
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SSHConnectionService {

    private final KeycloakService keycloakService;

    /**
     * SSH connection wrapper to encapsulate JSch objects
     */
    public static class SSHConnection {
        private final JSch jsch;
        private final Session session;
        private final ChannelShell channelShell;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final ExecutorService executorService;
        private Future<?> outputReaderTask;
        private boolean isConnected = false;

        public SSHConnection(JSch jsch, Session session, ChannelShell channelShell,
                InputStream inputStream, OutputStream outputStream) {
            this.jsch = jsch;
            this.session = session;
            this.channelShell = channelShell;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.executorService = Executors.newSingleThreadExecutor();
            this.isConnected = true;
        }

        public void sendInput(String input) throws IOException {
            if (!isConnected || outputStream == null) {
                throw new IOException("SSH connection not available");
            }

            log.debug("Sending input to SSH shell: {}", input.replaceAll(SSH_REGEX_NEWLINE, SSH_NEWLINE_ESCAPE));
            outputStream.write(input.getBytes(ENCODING_UTF8));
            outputStream.flush();
        }

        public void resizeTerminal(int cols, int rows) {
            if (!isConnected || channelShell == null) {
                log.warn("SSH session not connected, cannot resize terminal");
                return;
            }

            try {
                log.debug("Resizing terminal PTY to {}x{}", cols, rows);
                channelShell.setPtySize(cols, rows, cols * 8, rows * 16);
                log.debug("Terminal PTY resized successfully");
            } catch (Exception e) {
                log.error("Failed to resize terminal PTY", e);
            }
        }

        public void startOutputReader(Consumer<String> outputCallback) {
            outputReaderTask = executorService.submit(() -> {
                try {
                    processOutputReaderLoop(outputCallback);
                } catch (IOException e) {
                    handleOutputReaderIOException(e);
                } catch (InterruptedException e) {
                    handleOutputReaderInterruptedException(e);
                }
            });
        }

        private void processOutputReaderLoop(Consumer<String> outputCallback) throws IOException, InterruptedException {
            byte[] buffer = new byte[1024];
            log.debug("Started SSH output reader thread");

            while (isConnected && !Thread.currentThread().isInterrupted()) {
                if (inputStream.available() > 0) {
                    processAvailableData(buffer, outputCallback);
                } else {
                    Thread.sleep(10);
                }
            }

            log.debug("SSH output reader thread ended");
        }

        private void processAvailableData(byte[] buffer, Consumer<String> outputCallback) throws IOException {
            int bytesRead = inputStream.read(buffer);
            if (bytesRead > 0) {
                handleSuccessfulRead(buffer, bytesRead, outputCallback);
            } else if (bytesRead == -1) {
                handleStreamClosed();
            }
        }

        private void handleSuccessfulRead(byte[] buffer, int bytesRead, Consumer<String> outputCallback) {
            try {
                String output = new String(buffer, 0, bytesRead, ENCODING_UTF8);
                log.debug("Received SSH output: {} bytes", bytesRead);

                if (outputCallback != null) {
                    outputCallback.accept(output);
                }
            } catch (java.io.UnsupportedEncodingException e) {
                log.error("Unsupported encoding error", e);
            }
        }

        private void handleStreamClosed() {
            log.info("SSH input stream closed");
            // Break the loop by setting isConnected to false
            isConnected = false;
        }

        private void handleOutputReaderIOException(IOException e) {
            if (isConnected) {
                log.error("Error reading SSH output", e);
            }
        }

        private void handleOutputReaderInterruptedException(InterruptedException e) {
            log.debug("SSH output reader thread interrupted", e);
            Thread.currentThread().interrupt();
        }

        public boolean isConnected() {
            return isConnected;
        }

        public void disconnect() {
            isConnected = false;

            // Cancel output reader task
            if (outputReaderTask != null && !outputReaderTask.isDone()) {
                outputReaderTask.cancel(true);
            }

            // Close streams
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                log.warn("Error closing input stream", e);
            }

            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception e) {
                log.warn("Error closing output stream", e);
            }

            // Disconnect SSH components
            try {
                if (channelShell != null && channelShell.isConnected()) {
                    channelShell.disconnect();
                }
            } catch (Exception e) {
                log.warn("Error disconnecting channel", e);
            }

            try {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception e) {
                log.warn("Error disconnecting session", e);
            }

            // Shutdown executor
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
        }
    }

    /**
     * Create SSH connection to remote server
     */
    public SSHConnection createSSHConnection(String host, int port, AssetCredential sshCredential, String userKey)
            throws Exception {
        log.info("Creating SSH connection to {}@{}:{}", sshCredential.getUsername(), host, port);

        JSch jsch = new JSch();

        // Enable JSch debug logging for troubleshooting (more verbose)
        JSch.setLogger(new com.jcraft.jsch.Logger() {
            @Override
            public boolean isEnabled(int level) {
                return level >= com.jcraft.jsch.Logger.DEBUG; // Show all debug info
            }

            @Override
            public void log(int level, String message) {
                String levelStr = getLogLevelString(level);
                log.debug("[JSch-{}] {}", levelStr, message);
            }
        });

        Session session = null;
        ChannelShell channelShell = null;

        try {
            // Create SSH session
            session = jsch.getSession(sshCredential.getUsername(), host, port);

            // Configure SSH session
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("UserKnownHostsFile", "/dev/null");
            session.setConfig("PreferredAuthentications", "publickey,password");

            // Set up authentication
            setupAuthentication(jsch, session, sshCredential, userKey);

            // Connect with timeout
            log.debug("Connecting SSH session with 30 second timeout");
            log.debug("Attempting SSH connection to {}@{}:{}", sshCredential.getUsername(), host, port);
            session.connect(30000);
            log.info("SSH session connected successfully. Server version: {}", session.getServerVersion());

            // Open shell channel
            log.debug("Opening shell channel");
            channelShell = (ChannelShell) session.openChannel("shell");
            channelShell.setPtyType("xterm");
            channelShell.setPtySize(80, 24, 640, 480);

            // Get input/output streams
            InputStream inputStream = channelShell.getInputStream();
            OutputStream outputStream = channelShell.getOutputStream();

            // Connect the channel
            log.debug("Connecting shell channel with 10 second timeout");
            channelShell.connect(10000);

            log.info("SSH connection established successfully to {}:{}", host, port);

            return new SSHConnection(jsch, session, channelShell, inputStream, outputStream);

        } catch (Exception e) {
            // Cleanup on failure
            if (channelShell != null && channelShell.isConnected()) {
                channelShell.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }

            log.error("Failed to establish SSH connection to {}:{}", host, port, e);
            throw e;
        }
    }

    /**
     * Set up SSH authentication (password or key-based)
     */
    private void setupAuthentication(JSch jsch, Session session, AssetCredential sshCredential, String userKey)
            throws Exception {
        if (sshCredential.getSshKeyFile() != null && !sshCredential.getSshKeyFile().isEmpty()) {
            // Use SSH key authentication
            log.info("Setting up SSH key authentication");
            setupSSHKeyAuthentication(jsch, sshCredential, userKey);
        } else if (sshCredential.getPassword() != null && !sshCredential.getPassword().isEmpty()) {
            // Use password authentication
            log.info("Setting up password authentication");
            session.setPassword(sshCredential.getPassword());
        } else {
            throw new SecurityException("No authentication method available (no password or SSH key)");
        }
    }

    /**
     * Set up SSH key authentication with decryption
     */
    private void setupSSHKeyAuthentication(JSch jsch, AssetCredential sshCredential, String userKey) throws Exception {
        try {
            // Validate user encryption key
            if (userKey == null || userKey.isEmpty()) {
                throw new SecurityException("User encryption key not available");
            }

            // Decrypt the SSH private key
            String decryptedSSHKey = CommonUtils.decrypt(userKey, sshCredential.getSshKeyFile());
            log.debug("SSH private key decrypted successfully, length: {} chars", decryptedSSHKey.length());

            // Debug: Log first and last 50 characters to verify format
            String keyStart = getKeyStart(decryptedSSHKey);
            String keyEnd = getKeyEnd(decryptedSSHKey);
            log.debug("Decrypted key starts with: [{}]", keyStart.replaceAll(SSH_REGEX_NEWLINE, SSH_NEWLINE_ESCAPE));
            log.debug("Decrypted key ends with: [{}]", keyEnd.replaceAll(SSH_REGEX_NEWLINE, SSH_NEWLINE_ESCAPE));

            // Check for line ending issues and fix them
            String normalizedKey = decryptedSSHKey.replace("\r\n", "\n").replace("\r", "\n");
            if (!normalizedKey.equals(decryptedSSHKey)) {
                log.debug("Fixed line ending issues in SSH key");
                decryptedSSHKey = normalizedKey;
            }

            // Validate the decrypted key format
            validateSSHKeyFormat(decryptedSSHKey);

            // Log key type for debugging
            logSSHKeyType(decryptedSSHKey);

            // Use direct key content (modern JSch supports this)
            log.debug("Using direct private key content with modern JSch");
            String keyName = "ssh-key-" + sshCredential.getUsername() + "-" + System.currentTimeMillis();

            // Modern JSch can load keys directly from byte arrays
            jsch.addIdentity(keyName, decryptedSSHKey.getBytes(ENCODING_UTF8), null, null);
            log.debug("SSH private key loaded directly into JSch with name: {}", keyName);

            // Verify that the identity was added successfully
            List<?> identities = new ArrayList<>(jsch.getIdentityRepository().getIdentities());
            log.debug("Total identities loaded in JSch: {}", identities.size());
            for (int i = 0; i < identities.size(); i++) {
                Object identity = identities.get(i);
                log.debug("Identity {}: {}", i, identity.toString());
            }

            log.debug("SSH key authentication configured successfully with key name: {}", keyName);

        } catch (CommonUtils.CryptoException e) {
            log.error("Failed to decrypt SSH key", e);
            throw new SecurityException("Failed to decrypt SSH key", e);
        } catch (Exception e) {
            log.error("Failed to set up SSH key authentication", e);
            throw new SecurityException("Failed to set up SSH key authentication", e);
        }
    }

    /**
     * Helper method to get log level string - extracted from nested ternary
     */
    private static String getLogLevelString(int level) {
        if (level == com.jcraft.jsch.Logger.DEBUG) {
            return "DEBUG";
        } else if (level == com.jcraft.jsch.Logger.INFO) {
            return "INFO";
        } else if (level == com.jcraft.jsch.Logger.WARN) {
            return "WARN";
        } else if (level == com.jcraft.jsch.Logger.ERROR) {
            return "ERROR";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Extract first 50 characters of SSH key for debugging - extracted from nested
     * ternary
     */
    private static String getKeyStart(String decryptedSSHKey) {
        if (decryptedSSHKey.length() > 50) {
            return decryptedSSHKey.substring(0, 50);
        } else {
            return decryptedSSHKey;
        }
    }

    /**
     * Extract last 50 characters of SSH key for debugging - extracted from nested
     * ternary
     */
    private static String getKeyEnd(String decryptedSSHKey) {
        if (decryptedSSHKey.length() > 50) {
            return decryptedSSHKey.substring(decryptedSSHKey.length() - 50);
        } else {
            return "";
        }
    }

    /**
     * Validate SSH key format - extracted to reduce cognitive complexity
     */
    private static void validateSSHKeyFormat(String decryptedSSHKey) {
        if (!decryptedSSHKey.contains("-----BEGIN") || !decryptedSSHKey.contains("-----END")) {
            throw new SecurityException("Invalid SSH private key format after decryption");
        }
    }

    /**
     * Log SSH key type for debugging - extracted to reduce cognitive complexity
     */
    private static void logSSHKeyType(String decryptedSSHKey) {
        if (decryptedSSHKey.contains("-----BEGIN PRIVATE KEY-----")) {
            log.debug("Detected PKCS#8 private key format");
        } else if (decryptedSSHKey.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            log.debug("Detected PEM RSA private key format");
        } else if (decryptedSSHKey.contains("-----BEGIN OPENSSH PRIVATE KEY-----")) {
            log.debug("Detected OpenSSH private key format");
        } else {
            log.debug("Unknown private key format: {}",
                    decryptedSSHKey.substring(0, Math.min(50, decryptedSSHKey.length())));
        }
    }
}
