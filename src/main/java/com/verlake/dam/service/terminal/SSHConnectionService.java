package com.verlake.dam.service.terminal;

import com.jcraft.jsch.*;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.exception.TerminalInputException;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.SSHCommandUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.verlake.dam.utils.Constants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final AssetService assetService;
    private final UserService userService;
    private final KeycloakService keycloakService;
    
    // Session cache: key = assetId:userId, value = Session
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    
    // Session timeout in milliseconds (5 minutes)
    private static final int SESSION_TIMEOUT_MS = Constants.SSH_SESSION_TIMEOUT_MS;

    /**
     * SSH connection wrapper to encapsulate JSch objects
     */
    public static class SSHConnection {
        private final Session session;
        private final ChannelShell channelShell;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final ExecutorService executorService;
        private Future<?> outputReaderTask;
        private boolean isConnected = false;

        public SSHConnection(Session session, ChannelShell channelShell,
                InputStream inputStream, OutputStream outputStream) {
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
            } catch (RuntimeException e) {
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
            } catch (IOException e) {
                log.warn("Error closing input stream", e);
            }

            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                log.warn("Error closing output stream", e);
            }

            // Disconnect SSH components
            try {
                if (channelShell != null && channelShell.isConnected()) {
                    channelShell.disconnect();
                }
            } catch (RuntimeException e) {
                log.warn("Error disconnecting channel", e);
            }

            try {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (RuntimeException e) {
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
            throws JSchException, IOException {
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
            log.debug("Connecting SSH session with 5 minute timeout");
            log.debug("Attempting SSH connection to {}@{}:{}", sshCredential.getUsername(), host, port);
            session.connect(Constants.SSH_CONNECT_TIMEOUT_MS);
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
            log.debug("Connecting shell channel with 5 minute timeout");
            channelShell.connect(Constants.SSH_CHANNEL_TIMEOUT_MS);

            log.info("SSH connection established successfully to {}:{}", host, port);

            return new SSHConnection(session, channelShell, inputStream, outputStream);

        } catch (JSchException | IOException e) {
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
            throws JSchException {
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
    private void setupSSHKeyAuthentication(JSch jsch, AssetCredential sshCredential, String userKey) throws JSchException {
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
        } catch (JSchException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to set up SSH key authentication", e);
            throw new JSchException("Failed to set up SSH key authentication: " + e.getMessage(), e);
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

    /**
     * Execute SSH command on an asset (for Unix group operations)
     * This method uses session caching to reuse connections
     */
    public String executeCommand(Asset asset, String command) throws IOException {
        log.debug("Executing SSH command on asset {}: {}", asset.getId(), command);

        try {
            // Get asset credentials
            User currentUser = userService.getCurrentUser();
            AssetCredential sshCredential = assetService.getSSHCredentialsForAsset(asset.getId(), currentUser);
            if (sshCredential == null) {
                throw new IOException("No SSH credentials found for asset: " + asset.getId());
            }
            String userKey = keycloakService.getUserKey();

            // Get or create cached session
            Session session = getOrCreateSession(asset, sshCredential, currentUser, userKey);

            // Execute the command using the cached session
            return executeCommandOnSession(session, command);

        } catch (InterruptedException e) {
            // Restore interrupted status
            Thread.currentThread().interrupt();
            log.error("SSH command execution interrupted on asset {}: {}", asset.getId(), command, e);
            throw new IOException("SSH command execution was interrupted: " + e.getMessage(), e);
        } catch (JSchException | TerminalInputException e) {
            log.error("Failed to execute SSH command on asset {}: {}", asset.getId(), command, e);
            throw new IOException("Failed to execute SSH command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get or create a cached SSH session
     */
    private Session getOrCreateSession(Asset asset, AssetCredential credential, User user, String userKey) throws JSchException {
        String sessionKey = asset.getId() + ":" + user.getId();
        
        // Check if we have a cached session
        Session cachedSession = sessionCache.get(sessionKey);
        
        // Validate cached session
        if (cachedSession != null && cachedSession.isConnected()) {
            log.debug("Reusing cached SSH session for asset {} and user {}", asset.getId(), user.getId());
            return cachedSession;
        }
        
        // Remove invalid session from cache
        if (cachedSession != null) {
            log.debug("Cached session is disconnected, removing from cache");
            sessionCache.remove(sessionKey);
            try {
                cachedSession.disconnect();
            } catch (RuntimeException e) {
                log.debug("Error disconnecting stale session: {}", e.getMessage());
            }
        }
        
        // Create new session
        log.debug("Creating new SSH session for asset {} and user {}", asset.getId(), user.getId());
        Session newSession = createSession(asset, credential, userKey);
        
        // Cache the session
        sessionCache.put(sessionKey, newSession);
        
        return newSession;
    }
    
    /**
     * Create a new SSH session
     */
    private Session createSession(Asset asset, AssetCredential credential, String userKey) throws JSchException {
        JSch jsch = new JSch();
        
        String host = asset.getHostAddress();
        int port = asset.getPortNumber() != null ? Integer.parseInt(asset.getPortNumber()) : 22;
        String username = credential.getUsername();
        
        // Decrypt SSH key if available
        String decryptedSSHKey = null;
        if (credential.getSshKeyFile() != null && !credential.getSshKeyFile().isEmpty()) {
            try {
                decryptedSSHKey = CommonUtils.decrypt(userKey, credential.getSshKeyFile());
                byte[] privateKeyBytes = decryptedSSHKey.getBytes();
                jsch.addIdentity(username, privateKeyBytes, null, null);
                log.debug("Using SSH key authentication for user: {}", username);
            } catch (CommonUtils.CryptoException | JSchException e) {
                log.warn("Failed to decrypt SSH key, falling back to password authentication: {}", e.getMessage());
                decryptedSSHKey = null;
            }
        }
        
        // Create session
        Session session = jsch.getSession(username, host, port);
        
        // Set password if no SSH key
        if (decryptedSSHKey == null && credential.getPassword() != null) {
            session.setPassword(credential.getPassword());
            log.debug("Using password authentication for user: {}", username);
        }
        
        // Configure session
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(SESSION_TIMEOUT_MS);
        
        // Connect
        session.connect();
        log.info("SSH session established for asset {} on {}:{}", asset.getId(), host, port);
        
        return session;
    }
    
    /**
     * Execute command on an existing session
     */
    private String executeCommandOnSession(Session session, String command) throws JSchException, IOException, InterruptedException, TerminalInputException {
        ChannelExec channelExec = null;
        
        try {
            channelExec = setupAndConnectChannel(session, command);
            CommandOutputStreams streams = new CommandOutputStreams(
                channelExec.getInputStream(), 
                channelExec.getErrStream()
            );
            
            CommandOutputResult result = readCommandOutputStreams(channelExec, streams);
            validateCommandExitStatus(channelExec.getExitStatus(), result.getErrorOutput());
            
            return result.getOutput();
            
        } finally {
            disconnectChannel(channelExec);
        }
    }
    
    /**
     * Set up and connect the channel execution
     */
    private ChannelExec setupAndConnectChannel(Session session, String command) throws JSchException {
        ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
        channelExec.setCommand(command);
        channelExec.connect();
        return channelExec;
    }
    
    /**
     * Read all output from command execution streams
     */
    private CommandOutputResult readCommandOutputStreams(ChannelExec channelExec, CommandOutputStreams streams) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        byte[] buffer = new byte[1024];
        
        while (!isChannelReadComplete(channelExec, streams.getInputStream())) {
            readFromStream(streams.getInputStream(), buffer, output);
            readFromStream(streams.getErrorStream(), buffer, errorOutput);
            Thread.sleep(100);
        }
        
        return new CommandOutputResult(output.toString(), errorOutput.toString());
    }
    
    /**
     * Check if channel reading is complete
     */
    private boolean isChannelReadComplete(ChannelExec channelExec, InputStream inputStream) throws IOException {
        return channelExec.isClosed() && inputStream.available() == 0;
    }
    
    /**
     * Read all available data from a stream
     */
    private void readFromStream(InputStream stream, byte[] buffer, StringBuilder output) throws IOException {
        while (stream.available() > 0) {
            int bytesRead = stream.read(buffer, 0, 1024);
            if (bytesRead > 0) {
                output.append(new String(buffer, 0, bytesRead));
            }
        }
    }
    
    /**
     * Validate command exit status
     */
    private void validateCommandExitStatus(int exitStatus, String errorOutput) throws TerminalInputException {
        if (exitStatus != 0 && !errorOutput.isEmpty()) {
            throw new TerminalInputException("Command failed with exit status " + exitStatus + ": " + errorOutput);
        }
    }
    
    /**
     * Disconnect channel safely
     */
    private void disconnectChannel(ChannelExec channelExec) {
        if (channelExec != null && channelExec.isConnected()) {
            channelExec.disconnect();
        }
    }
    
    /**
     * Holder for command output streams
     */
    private static class CommandOutputStreams {
        private final InputStream inputStream;
        private final InputStream errorStream;
        
        public CommandOutputStreams(InputStream inputStream, InputStream errorStream) {
            this.inputStream = inputStream;
            this.errorStream = errorStream;
        }
        
        public InputStream getInputStream() {
            return inputStream;
        }
        
        public InputStream getErrorStream() {
            return errorStream;
        }
    }
    
    /**
     * Holder for command output result
     */
    private static class CommandOutputResult {
        private final String output;
        private final String errorOutput;
        
        public CommandOutputResult(String output, String errorOutput) {
            this.output = output;
            this.errorOutput = errorOutput;
        }
        
        public String getOutput() {
            return output;
        }
        
        public String getErrorOutput() {
            return errorOutput;
        }
    }
    
    /**
     * Clear session cache (e.g., when user logs out or credentials change)
     */
    public void clearSessionCache() {
        log.info("Clearing SSH session cache");
        sessionCache.forEach((key, session) -> {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (RuntimeException e) {
                log.debug("Error disconnecting session {}: {}", key, e.getMessage());
            }
        });
        sessionCache.clear();
    }
    
    /**
     * Clear session for specific asset and user
     */
    public void clearSession(Long assetId, Long userId) {
        String sessionKey = assetId + ":" + userId;
        Session session = sessionCache.remove(sessionKey);
        if (session != null) {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
                log.info("Cleared SSH session for asset {} and user {}", assetId, userId);
            } catch (RuntimeException e) {
                log.debug("Error disconnecting session: {}", e.getMessage());
            }
        }
    }

    /**
     * Execute SSH command and return only the command result (no connection messages)
     * This method establishes a clean SSH session, waits for prompt, executes command,
     * and returns only the command output without connection banners or prompts.
     */
    public String executeCommandWithCleanOutput(Asset asset, String command) throws IOException {
        log.debug("Executing clean SSH command on asset {}: {}", asset.getId(), command);

        try {
            // Get asset credentials
            User currentUser = userService.getCurrentUser();
            AssetCredential sshCredential = assetService.getSSHCredentialsForAsset(asset.getId(), currentUser);
            if (sshCredential == null) {
                throw new IOException("No SSH credentials found for asset: " + asset.getId());
            }
            String userKey = keycloakService.getUserKey();

            // Create SSH connection
            SSHConnection connection = createSSHConnection(
                    asset.getHostAddress(),
                    asset.getPortNumber() != null ? Integer.parseInt(asset.getPortNumber()) : 22,
                    sshCredential,
                    userKey
            );

            try {
                // Execute the command with clean output
                return executeCommandWithCleanOutputOnConnection(connection, command);
            } finally {
                // Always disconnect
                connection.disconnect();
            }

        } catch (JSchException e) {
            log.error("Failed to execute clean SSH command on asset {}: {}", asset.getId(), command, e);
            throw new IOException("Failed to execute clean SSH command: " + e.getMessage(), e);
        }
    }


    /**
     * Execute a command on an existing SSH connection and return only the command result
     * This method waits for the shell prompt, executes the command, and captures only
     * the command output without connection messages or prompts.
     */
    private String executeCommandWithCleanOutputOnConnection(SSHConnection connection, String command) throws IOException {
        StringBuilder output = new StringBuilder();
        StringBuilder commandOutput = new StringBuilder();
        boolean commandCompleted = false;
        int timeoutMs = Constants.SSH_COMMAND_TIMEOUT_MS; // 5 minute timeout
        int checkIntervalMs = 100; // Check every 100ms
        int elapsedMs = 0;

        // Start output reader
        connection.startOutputReader(data -> {
            synchronized (output) {
                output.append(data);
            }
        });

        // Wait for initial prompt (connection established)
        log.debug("Waiting for SSH prompt...");
        try {
            Thread.sleep(1000); // Wait 1 second for initial connection
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Connection wait interrupted", e);
        }

        // Send the command
        log.debug("Sending command: {}", command);
        connection.sendInput(command + "\n");

        // Wait for command completion with timeout
        while (!commandCompleted && elapsedMs < timeoutMs) {
            try {
                Thread.sleep(checkIntervalMs);
                elapsedMs += checkIntervalMs;

                synchronized (output) {
                    String currentOutput = output.toString();
                    
                    // Check if we have a prompt after the command (indicating completion)
                    if (currentOutput.contains(command) && 
                        (currentOutput.contains("$ ") || currentOutput.contains("# ") || 
                         currentOutput.contains("~$") || currentOutput.contains("~#"))) {
                        commandCompleted = true;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Command execution interrupted", e);
            }
        }

        if (!commandCompleted) {
            log.warn("Command execution timed out after {}ms", timeoutMs);
        }

        // Extract only the command output (between command and next prompt)
        synchronized (output) {
            String fullOutput = output.toString();
            commandOutput = extractCommandOutput(fullOutput, command);
        }

        log.debug("Extracted command output: {}", commandOutput.toString());
        return commandOutput.toString();
    }

    /**
     * Extract only the command output from the full terminal output
     */
    private StringBuilder extractCommandOutput(String fullOutput, String command) {
        String result = SSHCommandUtils.extractCommandOutput(fullOutput, command);
        return new StringBuilder(result);
    }
}
