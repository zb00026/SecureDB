package com.verlake.dam.service.terminal;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.entity.terminal.TerminalSession;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.utils.Constants;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import com.verlake.dam.repository.assets.AssetRepository;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.service.auth.AuthService;
import com.verlake.dam.service.auth.TokenService;
import com.verlake.dam.service.auth.TokenServiceManager;
import com.verlake.dam.service.auth.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.verlake.dam.exception.TerminalInputException;

/**
 * Service for managing terminal connections to Unix servers
 * This service handles the WebSocket to SSH connection bridging
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TerminalService {

    private final AssetRepository assetRepository;
    private final AssetService assetService;
    private final UserService userService;
    private final AuthService authService;
    private final TokenServiceManager tokenServiceManager;
    private final KeycloakService keycloakService;
    private final SSHConnectionService sshConnectionService;
    private final TerminalRecordingService terminalRecordingService;

    // Store active terminal sessions
    private final ConcurrentMap<String, TerminalSession> activeSessions = new ConcurrentHashMap<>();
    
    // Store current command being executed for each session
    private final ConcurrentMap<String, String> currentCommands = new ConcurrentHashMap<>();
    
    // Store command output buffer for each session
    private final ConcurrentMap<String, StringBuilder> commandOutputBuffers = new ConcurrentHashMap<>();
    
    // Store command start time for execution timing
    private final ConcurrentMap<String, Long> commandStartTimes = new ConcurrentHashMap<>();
    
    // Store terminal state before command execution (to calculate command result)
    private final ConcurrentMap<String, String> preCommandState = new ConcurrentHashMap<>();
    
    // Track current command being typed for keyboard events
    private final ConcurrentMap<String, StringBuilder> currentCommandBuffers = new ConcurrentHashMap<>();

    /**
     * Create a new terminal session for a Unix server
     */
    public TerminalSession createSession(Long assetId, String host, int port) {
        log.info("Creating terminal session for asset ID: {} to {}:{}", assetId, host, port);

        Asset asset = assetRepository.findByIdAndDeletedFalse(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        if (asset.getType() != com.verlake.dam.enums.AssetType.UNIX_SERVER) {
            throw new IllegalArgumentException("Asset must be of type UNIX_SERVER");
        }

        User currentUser = userService.getCurrentUser();

        // Get SSH credentials for this user and asset
        AssetCredential sshCredential = assetService.getSSHCredentialsForAsset(assetId, currentUser);
        if (sshCredential == null) {
            throw new SecurityException("No SSH credentials found for this asset");
        }

        // Create terminal session using builder pattern
        TerminalSession session = TerminalSession.builder()
                .assetId(assetId)
                .host(host)
                .port(port)
                .sshCredential(sshCredential)
                .createdAt(System.currentTimeMillis())
                .build();
        String sessionId = session.getSessionId(); // Use TerminalSession's session ID
        activeSessions.put(sessionId, session);

        log.info("Terminal session created successfully. Session ID: {}", sessionId);
        return session;
    }

    /**
     * Create a new terminal session for a Unix server with JWT token authentication
     */
    public TerminalSession createSessionWithToken(Long assetId, String token, AuthProvider authProvider,
            String clientIp, String userAgent) {
        log.info("Creating terminal session with token for asset ID: {}", assetId);

        // Authenticate user from token using AuthService
        User currentUser = authenticateUserFromToken(token, authProvider);
        if (currentUser == null) {
            throw new SecurityException("Invalid authentication token");
        }

        // Get user's encryption key early while we have the authentication context
        String userKey = keycloakService.getUserKeyViaAccountApi(token);
        if (userKey == null || userKey.isEmpty()) {
            throw new SecurityException("User encryption key not available during authentication");
        }

        Asset asset = assetRepository.findByIdAndDeletedFalse(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        if (asset.getType() != com.verlake.dam.enums.AssetType.UNIX_SERVER) {
            throw new IllegalArgumentException("Asset must be of type UNIX_SERVER");
        }

        // Get SSH credentials for this user and asset
        AssetCredential sshCredential = assetService.getSSHCredentialsForAsset(assetId, currentUser);
        if (sshCredential == null) {
            throw new SecurityException("No SSH credentials found for this asset");
        }

        // Use host and port from Asset entity, not from frontend parameters
        String actualHost = asset.getHostAddress();
        int actualPort = Integer.parseInt(asset.getPortNumber());

        log.info("Creating terminal session to real server {}:{} with username: {}",
                actualHost, actualPort, sshCredential.getUsername());

        // Create terminal session with real asset connection details using builder
        // pattern
        TerminalSession session = TerminalSession.builder()
                .assetId(assetId)
                .host(actualHost)
                .port(actualPort)
                .sshCredential(sshCredential)
                .userKey(userKey)
                .createdAt(System.currentTimeMillis())
                .build();
        String sessionId = session.getSessionId();
        activeSessions.put(sessionId, session);

        // Start session recording for audit purposes
        try {
            terminalRecordingService.startSessionRecording(
                    sessionId, asset, currentUser, sshCredential.getUsername(),
                    actualHost, actualPort, clientIp, userAgent, "80x24");
            log.info("Session recording started for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to start session recording for session: {}", sessionId, e);
        }

        log.info("Terminal session created successfully. Session ID: {}", sessionId);
        return session;
    }

    /**
     * Get an active terminal session
     */
    public TerminalSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * Establish SSH connection for a terminal session
     * 
     * @param sessionId      the session ID
     * @param outputCallback callback to receive terminal output
     * @throws Exception if connection fails
     */
    public void establishSSHConnection(String sessionId, java.util.function.Consumer<String> outputCallback)
            throws Exception {
        TerminalSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Terminal session not found: " + sessionId);
        }

        if (session.isConnected()) {
            log.warn("SSH connection already established for session: {}", sessionId);
            return;
        }

        try {
            session.setConnecting(true);

            // Get user's encryption key from the session (stored during authentication)
            String userKey = session.getUserKey();
            if (userKey == null || userKey.isEmpty()) {
                throw new SecurityException("User encryption key not available in session");
            }

            // Create SSH connection using the service
            SSHConnectionService.SSHConnection sshConnection = sshConnectionService.createSSHConnection(
                    session.getHost(), session.getPort(), session.getSshCredential(), userKey);

            // Set up output reader with recording
            sshConnection.startOutputReader(output -> {
                // Buffer output for command audit
                bufferCommandOutput(sessionId, output);
                
                // Record the output for audit
                try {
                    terminalRecordingService.recordOutput(sessionId, output);
                } catch (Exception e) {
                    log.error("Failed to record output for session: {}", sessionId, e);
                }

                // Check if command execution is complete and save to history
                checkAndSaveCommandCompletion(sessionId, output);

                // Call the original callback
                if (outputCallback != null) {
                    outputCallback.accept(output);
                }
            });

            // Store connection in session
            session.setSSHConnection(sshConnection);

            log.info("SSH connection established for session: {}", sessionId);
        } catch (Exception e) {
            session.setConnecting(false);
            session.setConnected(false);
            log.error("Failed to establish SSH connection for session: {}", sessionId, e);
            throw e;
        }
    }

    /**
     * Send keyboard input directly to SSH terminal
     * This allows native terminal handling of all keyboard events including Tab completion
     */
    public void sendKeyboardInput(String sessionId, String keySequence) throws TerminalInputException {
        TerminalSession session = validateSessionAndConnection(sessionId);
        SSHConnectionService.SSHConnection sshConnection = (SSHConnectionService.SSHConnection) session.getSSHConnection();

        try {
            handleKeyboardInput(sessionId, keySequence);
            sshConnection.sendInput(keySequence);
            logKeyboardInputSent(sessionId, keySequence);
        } catch (IOException e) {
            throw new TerminalInputException("Failed to send keyboard input to SSH session", e);
        }
    }

    /**
     * Validate session and connection
     */
    private TerminalSession validateSessionAndConnection(String sessionId) throws TerminalInputException {
        TerminalSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new TerminalInputException("Terminal session not found: " + sessionId);
        }

        SSHConnectionService.SSHConnection sshConnection = (SSHConnectionService.SSHConnection) session.getSSHConnection();
        if (sshConnection == null) {
            throw new TerminalInputException("SSH connection not established");
        }

        return session;
    }

    /**
     * Handle different types of keyboard input
     */
    private void handleKeyboardInput(String sessionId, String keySequence) {
        if (isEnterKey(keySequence)) {
            handleEnterKey(sessionId);
        } else if (isBackspaceKey(keySequence)) {
            handleBackspaceKey(sessionId);
        } else if (isCtrlCKey(keySequence)) {
            handleCtrlCKey(sessionId);
        } else if (isPrintableCharacter(keySequence)) {
            handlePrintableCharacter(sessionId, keySequence);
        } else {
            logOtherKeySequence(keySequence);
        }
    }

    /**
     * Check if key sequence is Enter key
     */
    private boolean isEnterKey(String keySequence) {
        return keySequence.equals("\n") || keySequence.equals("\r") || keySequence.equals(Constants.REGEX_CRLF_PATTERN);
    }

    /**
     * Check if key sequence is Backspace key
     */
    private boolean isBackspaceKey(String keySequence) {
        return keySequence.equals("\b") || keySequence.equals("\u007f");
    }

    /**
     * Check if key sequence is Ctrl+C key
     */
    private boolean isCtrlCKey(String keySequence) {
        return keySequence.equals("\u0003");
    }

    /**
     * Check if key sequence is a printable character
     */
    private boolean isPrintableCharacter(String keySequence) {
        return keySequence.length() == 1 && keySequence.charAt(0) >= 32 && keySequence.charAt(0) <= 126;
    }

    /**
     * Handle Enter key press
     */
    private void handleEnterKey(String sessionId) {
        String currentCommand = getCurrentCommandFromBuffer(sessionId);
        
        if (currentCommand != null && !currentCommand.trim().isEmpty()) {
            storeCommandForExecution(sessionId, currentCommand);
            log.info("🚀 Command execution detected for session: {} command: [{}]", sessionId, currentCommand);
        } else {
            log.debug("No command to execute for session: {} - currentCommand: [{}]", sessionId, currentCommand);
        }
        
        currentCommandBuffers.remove(sessionId);
    }

    /**
     * Store command for execution tracking
     */
    private void storeCommandForExecution(String sessionId, String currentCommand) {
        currentCommands.put(sessionId, currentCommand);
        commandStartTimes.put(sessionId, System.currentTimeMillis());
        capturePreCommandState(sessionId);
    }

    /**
     * Handle Backspace key press
     */
    private void handleBackspaceKey(String sessionId) {
        StringBuilder buffer = currentCommandBuffers.get(sessionId);
        if (buffer != null && !buffer.isEmpty()) {
            String before = buffer.toString();
            buffer.setLength(buffer.length() - 1);
            String after = buffer.toString();
            log.info("🔙 Backspace detected - before: [{}], after: [{}]", before, after);
        } else {
            log.info("🔙 Backspace detected but no buffer or empty buffer");
        }
    }

    /**
     * Handle Ctrl+C key press
     */
    private void handleCtrlCKey(String sessionId) {
        currentCommandBuffers.remove(sessionId);
    }

    /**
     * Handle printable character input
     */
    private void handlePrintableCharacter(String sessionId, String keySequence) {
        StringBuilder buffer = currentCommandBuffers.computeIfAbsent(sessionId, k -> new StringBuilder());
        String before = buffer.toString();
        buffer.append(keySequence);
        String after = buffer.toString();
        log.info("📝 Printable character added - char: [{}], before: [{}], after: [{}]", keySequence, before, after);
    }

    /**
     * Log other key sequences
     */
    private void logOtherKeySequence(String keySequence) {
        log.info("❓ Other key sequence: [{}] (length: {}, char: {})", 
                keySequence.replace("\t", Constants.REGEX_ESCAPE_BACKSLASH_SIMPLE + "t").replace("\b", Constants.REGEX_ESCAPE_BACKSLASH_SIMPLE + "b"), 
                keySequence.length(), 
                !keySequence.isEmpty() ? (int)keySequence.charAt(0) : -1);
    }

    /**
     * Log keyboard input sent
     */
    private void logKeyboardInputSent(String sessionId, String keySequence) {
        log.debug("Keyboard input sent to session: {} - sequence: [{}]", sessionId, 
                 keySequence.replace("\t", Constants.REGEX_ESCAPE_BACKSLASH_SIMPLE + "t").replace("\n", Constants.REGEX_ESCAPE_BACKSLASH_SIMPLE + "n"));
    }

    /**
     * Get current command from buffer
     */
    private String getCurrentCommandFromBuffer(String sessionId) {
        StringBuilder buffer = currentCommandBuffers.get(sessionId);
        return buffer != null ? buffer.toString() : null;
    }
    
    /**
     * Safely record complete command with proper error handling
     */
    private void recordCompleteCommandSafely(String sessionId, String currentCommand, String commandResult, Long executionTimeMs) {
        try {
            log.debug("Recording complete command for session: {} command: [{}] with result length: {} execution time: {}ms", 
                     sessionId, currentCommand, commandResult != null ? commandResult.length() : 0, executionTimeMs);
            
            // Record command with complete details
            terminalRecordingService.recordCommandWithDetails(
                sessionId,
                currentCommand,
                commandResult, // ✅ Now we have the actual command result!
                executionTimeMs // ✅ Now we have the execution time!
            );
            
            log.info("✅ Successfully recorded complete command with result for command: [{}] in session: {}", currentCommand, sessionId);
        } catch (Exception e) {
            log.error("❌ Failed to record complete command for session: {} command: [{}]", sessionId, currentCommand, e);
        }
    }
    
    /**
     * Resize terminal
     */
    public void resizeTerminal(String sessionId, int cols, int rows) {
        TerminalSession session = activeSessions.get(sessionId);
        if (session == null) {
            log.warn("Terminal session not found: {}", sessionId);
            return;
        }

        SSHConnectionService.SSHConnection sshConnection = (SSHConnectionService.SSHConnection) session
                .getSSHConnection();

        if (sshConnection != null) {
            sshConnection.resizeTerminal(cols, rows);

            // Update terminal size in recording
            try {
                terminalRecordingService.updateTerminalSize(sessionId, cols, rows);
            } catch (Exception e) {
                log.error("Failed to update terminal size in recording for session: {}", sessionId, e);
            }
        }
    }

    /**
     * Close a terminal session
     */
    public void closeSession(String sessionId) {
        TerminalSession session = activeSessions.remove(sessionId);
        if (session != null) {
            log.debug("Closing terminal session: {}", sessionId);
            
            // End session recording
            try {
                terminalRecordingService.endSessionRecording(sessionId);
                log.debug("Session recording ended for session: {}", sessionId);
            } catch (Exception e) {
                log.error("Failed to end session recording for session: {}", sessionId, e);
            }

            // Close SSH connection
            SSHConnectionService.SSHConnection sshConnection = (SSHConnectionService.SSHConnection) session
                    .getSSHConnection();
            if (sshConnection != null) {
                try {
                    sshConnection.disconnect();
                    log.debug("SSH connection closed for session: {}", sessionId);
                } catch (Exception e) {
                    log.error("Error closing SSH connection for session: {}", sessionId, e);
                }
            }

            // Close session
            try {
                session.close();
                log.debug("Terminal session closed: {}", sessionId);
            } catch (Exception e) {
                log.error("Error closing terminal session: {}", sessionId, e);
            }
            
            // Clean up current command tracking, output buffer, start time, pre-command state, and command buffer
            currentCommands.remove(sessionId);
            commandOutputBuffers.remove(sessionId);
            commandStartTimes.remove(sessionId);
            preCommandState.remove(sessionId);
            currentCommandBuffers.remove(sessionId);
            
            log.info("Terminal session closed successfully. Session ID: {}", sessionId);
        } else {
            log.warn("Attempted to close non-existent terminal session: {}", sessionId);
        }
    }

    /**
     * Clear all active sessions (used during server startup)
     * This ensures no stale sessions remain in memory after server restart
     */
    public void clearAllActiveSessions() {
        int sessionCount = activeSessions.size();
        
        // Clear all tracking maps
        currentCommands.clear();
        commandOutputBuffers.clear();
        commandStartTimes.clear();
        preCommandState.clear();
        currentCommandBuffers.clear();
        activeSessions.clear();
        
        log.info("Cleared {} active terminal sessions from memory during startup", sessionCount);
    }
    
    // Tab completion is now handled natively by the SSH terminal
    // All keyboard events (including Tab) are forwarded directly to SSH

    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> {
            TerminalSession session = entry.getValue();
            if (session.isExpired()) {
                session.close();
                log.debug("Cleaned up expired terminal session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Authenticate user from JWT token using AuthService
     */
    private User authenticateUserFromToken(String token, AuthProvider authProvider) {
        try {
            // Create a UserDTO with the token and auth provider
            UserDTO userDto = new UserDTO();
            userDto.setToken(token);
            userDto.setAuthProvider(authProvider);

            // Get the appropriate TokenService based on auth provider
            TokenService tokenService = getTokenService(authProvider);

            // Use AuthService to validate token and get user
            User user = authService.authenticateUser(userDto, tokenService);

            log.info("User authenticated successfully from token: {}", user.getEmail());
            return user;

        } catch (Exception e) {
            log.error("Failed to authenticate user from token", e);
            return null;
        }
    }

    /**
     * Get the appropriate TokenService based on auth provider
     */
    private TokenService getTokenService(AuthProvider authProvider) {
        TokenService tokenService = tokenServiceManager.getService(authProvider);
        if (tokenService == null) {
            log.error("No TokenService found for provider: {}", authProvider);
            throw new SecurityException("Authentication provider not supported: " + authProvider);
        }
        return tokenService;
    }
    
    /**
     * Buffer command output for audit
     */
    private void bufferCommandOutput(String sessionId, String output) {
        try {
            String currentCommand = currentCommands.get(sessionId);
            if (currentCommand == null) {
                // Always buffer output, even without current command (for initial prompt capture)
                StringBuilder outputBuffer = commandOutputBuffers.computeIfAbsent(sessionId, k -> new StringBuilder());
                outputBuffer.append(output);
                return;
            }
            
            // Get or create output buffer for this session
            StringBuilder outputBuffer = commandOutputBuffers.computeIfAbsent(sessionId, k -> new StringBuilder());
            outputBuffer.append(output);
            log.trace("Buffered {} chars for session: {} (current command: {})", output.length(), sessionId, currentCommand);
            
        } catch (Exception e) {
            log.error("Error buffering command output for session: {}", sessionId, e);
        }
    }
    
    /**
     * Check if command execution is complete and save to history
     */
    private void checkAndSaveCommandCompletion(String sessionId, String output) {
        try {
            String currentCommand = currentCommands.get(sessionId);
            if (currentCommand == null) {
                return; // No command being executed
            }
            
            TerminalSession session = activeSessions.get(sessionId);
            if (session == null) {
                return; // Session not found
            }
            
            // Get the complete buffered output (including the new chunk)
            StringBuilder outputBuffer = commandOutputBuffers.get(sessionId);
            String completeOutput = outputBuffer != null ? outputBuffer.toString() : "";
            
            // Check if the COMPLETE output contains a prompt (indicating command completion)
            boolean promptDetected = isPromptDetected(completeOutput);
            log.trace("Checking completion for session: {} - prompt detected: {} - current chunk: [{}] - complete output: [{}]", 
                     sessionId, promptDetected, output.replace(Constants.REGEX_CRLF_PATTERN, Constants.REGEX_CRLF_ESCAPE), completeOutput.replace(Constants.REGEX_CRLF_PATTERN, Constants.REGEX_CRLF_ESCAPE));
            
            if (promptDetected) {
                
                // Calculate the actual command result by comparing before/after states
                String commandResult = calculateCommandResult(sessionId, completeOutput);
                
                // Calculate execution time
                Long startTime = commandStartTimes.get(sessionId);
                Long executionTimeMs = startTime != null ? System.currentTimeMillis() - startTime : null;
                
                // Record the complete command with all details
                recordCompleteCommandSafely(sessionId, currentCommand, commandResult, executionTimeMs);
                
                // Command execution is complete, save to history
                session.addToHistory(currentCommand);
                log.debug("Command completed and saved to history: {} for session: {}", currentCommand, sessionId);
                
                // Clear the current command, output buffer, start time, and pre-command state
                currentCommands.remove(sessionId);
                commandOutputBuffers.remove(sessionId);
                commandStartTimes.remove(sessionId);
                preCommandState.remove(sessionId);
            }
            
        } catch (Exception e) {
            log.error("Error checking command completion for session: {}", sessionId, e);
        }
    }
    
    /**
     * Capture terminal state before command execution
     */
    private void capturePreCommandState(String sessionId) {
        try {
            // Get the current output buffer content as the pre-command state
            StringBuilder outputBuffer = commandOutputBuffers.get(sessionId);
            String currentState = outputBuffer != null ? outputBuffer.toString() : "";
            preCommandState.put(sessionId, currentState);
            log.debug("Captured pre-command state for session: {} with length: {}", sessionId, currentState.length());
        } catch (Exception e) {
            log.error("Error capturing pre-command state for session: {}", sessionId, e);
        }
    }
    
    /**
     * Calculate the actual command result by comparing before/after states
     */
    private String calculateCommandResult(String sessionId, String completeOutput) {
        try {
            String preState = preCommandState.get(sessionId);
            if (preState == null) {
                log.warn("No pre-command state found for session: {}, returning complete output", sessionId);
                return completeOutput;
            }
            
            // Remove the pre-command state from the complete output to get just the command result
            String commandResult = completeOutput;
            if (completeOutput.startsWith(preState)) {
                commandResult = completeOutput.substring(preState.length());
            }
            
            // Clean up the result by removing command echo and final prompt
            commandResult = cleanCommandResult(commandResult);
            
            log.debug("Calculated command result for session: {} - pre-state length: {}, complete output length: {}, result length: {}", 
                     sessionId, preState.length(), completeOutput.length(), commandResult.length());
            
            return commandResult;
            
        } catch (Exception e) {
            log.error("Error calculating command result for session: {}", sessionId, e);
            return completeOutput; // Fallback to complete output
        }
    }
    
    /**
     * Clean up command result by removing command echo and final prompt
     */
    private String cleanCommandResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            return result;
        }
        
        log.trace("cleanCommandResult - input: [{}]", result.replace(Constants.REGEX_CRLF_PATTERN, Constants.REGEX_CRLF_ESCAPE));
        
        // First, remove ANSI escape sequences using bounded quantifiers for security
        String cleanResult = result.replaceAll("\\x1b\\[[0-9;]{0,50}[a-zA-Z]", "").replaceAll("\\x1b\\[\\?[0-9;]{0,50}[a-zA-Z]", "");
        
        log.trace("cleanCommandResult - after ANSI cleanup: [{}]", cleanResult.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_NEWLINE_ESCAPE));
        
        // Remove trailing prompt patterns (but preserve the command output)
        // Using secure regex patterns following OWASP guidelines to prevent ReDoS attacks
        // Use possessive quantifiers and bounded quantifiers for security
        cleanResult = cleanResult.replaceAll("\\$\\s*+$", "");
        cleanResult = cleanResult.replaceAll("#\\s*+$", "");
        cleanResult = cleanResult.replaceAll(">\\s*+$", "");
        
        // Use possessive quantifiers and bounded quantifiers for user@host patterns
        // Limit path matching to reasonable length to prevent exponential backtracking
        cleanResult = cleanResult.replaceAll("\\w++@\\w++:[\\S]{0,200}\\$\\s*+$", "");
        cleanResult = cleanResult.replaceAll("\\w++@\\w++:[\\S]{0,200}#\\s*+$", "");
        cleanResult = cleanResult.replaceAll("\\[\\w++@\\w++\\s++\\w++\\]\\$\\s*+$", "");
        cleanResult = cleanResult.replaceAll("\\[\\w++@\\w++\\s++\\w++\\]#\\s*+$", "");
        
        // Remove command echo (the command itself being displayed at the beginning)
        // Split into lines and remove the first line if it's just the command
        String[] lines = cleanResult.split("\n");
        if (lines.length > 1) {
            String firstLine = lines[0].trim();
            // If the first line is just a simple command (no prompt), remove it
            if (firstLine.matches("^[a-zA-Z0-9_\\-\\./]+$") && !firstLine.contains("@") && !firstLine.contains("$") && !firstLine.contains("#")) {
                // Remove the first line (command echo)
                cleanResult = String.join("\n", Arrays.copyOfRange(lines, 1, lines.length));
                log.trace("cleanCommandResult - removed command echo: [{}]", firstLine);
            }
        }
        
        // Trim whitespace
        cleanResult = cleanResult.trim();
        
        log.trace("cleanCommandResult - final result: [{}]", cleanResult.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_NEWLINE_ESCAPE));
        
        return cleanResult;
    }
    
    /**
     * Detect if output contains a shell prompt (indicating command completion)
     */
    private boolean isPromptDetected(String output) {
        if (output == null || output.trim().isEmpty()) {
            return false;
        }
        
        // Remove ANSI escape sequences for cleaner pattern matching using bounded quantifiers
        String cleanOutput = output.replaceAll("\\x1b\\[[0-9;]{0,50}[a-zA-Z]", "").replaceAll("\\x1b\\[\\?[0-9;]{0,50}[a-zA-Z]", "");
        
        log.trace("isPromptDetected - original length: {} clean length: {}", output.length(), cleanOutput.length());
        log.trace("isPromptDetected - original: [{}] clean: [{}]", 
                 output.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_NEWLINE_ESCAPE), 
                 cleanOutput.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_NEWLINE_ESCAPE));
        
        // Common prompt patterns using secure regex patterns following OWASP guidelines
        // Use possessive quantifiers and bounded quantifiers to prevent ReDoS attacks
        String[] promptPatterns = {
            "\\$\\s*+$",                    // $ at end of line (possessive)
            "#\\s*+$",                      // # at end of line (root prompt, possessive)
            ">\\s*+$",                      // > at end of line (possessive)
            "\\w++@\\w++:[\\S]{0,200}\\$\\s*+$",      // user@host:path$ pattern (bounded, possessive)
            "\\w++@\\w++:[\\S]{0,200}#\\s*+$",       // user@host:path# pattern (root, bounded, possessive)
            "\\[\\w++@\\w++\\s++\\w++\\]\\$\\s*+$", // [user@host dir]$ pattern (possessive)
            "\\[\\w++@\\w++\\s++\\w++\\]#\\s*+$",    // [user@host dir]# pattern (root, possessive)
            "\\w++@\\w++\\$\\s*+$",           // user@host$ pattern (no path, possessive)
            "\\w++@\\w++#\\s*+$"              // user@host# pattern (root, no path, possessive)
        };
        
        // Check each pattern using secure approach with possessive quantifiers
        for (String pattern : promptPatterns) {
            // Use direct pattern matching with possessive quantifiers for security
            if (cleanOutput.matches(pattern)) {
                log.debug("✅ Prompt detected with pattern: {} in output: [{}]", pattern, cleanOutput.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_NEWLINE_ESCAPE));
                return true;
            }
        }
        
        // Additional check: look for prompt at the end of the output
        String lastLine = cleanOutput.trim();
        if (lastLine.endsWith("$") || lastLine.endsWith("#") || lastLine.endsWith(">")) {
            log.debug("✅ Prompt detected at end of output: [{}]", lastLine);
            return true;
        }
        
        log.trace("❌ No prompt detected in output: [{}]", cleanOutput.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_NEWLINE_ESCAPE));
        return false;
    }

}
