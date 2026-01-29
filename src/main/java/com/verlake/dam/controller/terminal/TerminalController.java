package com.verlake.dam.controller.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.dto.unix.FolderAccessRequestDTO;
import com.verlake.dam.entity.dto.unix.UnixFolderSuggestion;
import com.verlake.dam.entity.terminal.TerminalSession;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.terminal.TerminalService;
import com.verlake.dam.service.unix.UnixGroupService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.verlake.dam.utils.Constants.*;

@Component
@Slf4j
public class TerminalController extends TextWebSocketHandler {
    
    private final TerminalService terminalService;
    private final UnixGroupService unixGroupService;
    private final AssetService assetService;
    private final ObjectMapper objectMapper;
    
    // Constructor logging to verify the component is loaded
    public TerminalController(TerminalService terminalService, UnixGroupService unixGroupService, AssetService assetService, ObjectMapper objectMapper) {
        this.terminalService = terminalService;
        this.unixGroupService = unixGroupService;
        this.assetService = assetService;
        this.objectMapper = objectMapper;
        log.info("TerminalController initialized successfully");
    }
    
    // Store WebSocket sessions mapped to terminal sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Store session metadata for each WebSocket session
    private final Map<String, Map<String, String>> sessionMetadata = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("=== WebSocket connection established successfully ===");
        log.info(Constants.LOG_SESSION_ID, session.getId());
        log.info("URI: {}", session.getUri());
        log.info("Remote Address: {}", session.getRemoteAddress());
        
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        // Determine connection type based on URL path
        String path = session.getUri().getPath();
        boolean isUnixGroupConnection = path.contains("/unix-groups");
        
        // Get client IP address from WebSocket session
        String clientIp = getClientIpFromWebSocketSession(session);
        String userAgent = getUserAgentFromWebSocketSession(session);
        
        // Store client information in session metadata
        Map<String, String> metadata = new ConcurrentHashMap<>();
        metadata.put(Constants.SESSION_METADATA_CLIENT_IP, clientIp);
        metadata.put(Constants.SESSION_METADATA_USER_AGENT, userAgent);
        metadata.put(Constants.CONNECTION_TYPE_FIELD, isUnixGroupConnection ? Constants.CONNECTION_TYPE_UNIX_GROUPS : Constants.CONNECTION_TYPE_TERMINAL);
        sessionMetadata.put(sessionId, metadata);
        
        if (isUnixGroupConnection) {
            // Handle Unix group connection
            handleUnixGroupConnection(session);
        } else {
            // Handle terminal connection (existing logic)
            handleTerminalConnection(session);
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            
            // Get connection type from session metadata
            Map<String, String> metadata = sessionMetadata.get(session.getId());
            String connectionType = metadata != null ? metadata.get(Constants.CONNECTION_TYPE_FIELD) : Constants.CONNECTION_TYPE_TERMINAL;
            
            // Check for action type
            String action = (String) data.get(TERMINAL_ACTION);
            if (action != null) {
                if (Constants.CONNECTION_TYPE_UNIX_GROUPS.equals(connectionType)) {
                    // Handle Unix group actions
                    handleUnixGroupAction(session, action, data);
                } else {
                    // Handle terminal actions (existing logic)
                    handleTerminalAction(session, action, data);
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            
            Map<String, Object> errorResponse = Map.of(
                "type", Constants.WS_MESSAGE_TYPE_ERROR,
                Constants.JSON_FIELD_MESSAGE, Constants.MSG_FAILED_TO_PROCESS_MESSAGE + e.getMessage()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }
    
    /**
     * Handle Unix group connection establishment
     */
    private void handleUnixGroupConnection(WebSocketSession session) throws Exception {
        log.info("Unix Group WebSocket connection established");

        // Send connection ready message (assetId will be provided in first message)
        Map<String, Object> response = Map.of(
                "type", Constants.WS_MESSAGE_TYPE_CONNECTION_READY,
                Constants.JSON_FIELD_MESSAGE, "Unix Group WebSocket connection ready. Please authenticate with assetId."
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * Handle terminal connection establishment (existing logic)
     */
    private void handleTerminalConnection(WebSocketSession session) throws Exception {
        // Extract parameters from query string
        String query = session.getUri().getQuery();
        Map<String, String> params = parseQueryString(query);
        
        // Validate required parameters
        if (!params.containsKey(TERMINAL_ASSET_ID) || !params.containsKey(TERMINAL_TOKEN) ||
            !params.containsKey(TERMINAL_AUTH_PROVIDER)) {
            log.error("Missing required parameters in WebSocket connection");
            Map<String, Object> errorResponse = Map.of(
                TERMINAL_TYPE, TERMINAL_CONNECTION_ERROR,
                TERMINAL_MESSAGE, MSG_MISSING_REQUIRED_PARAMS
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
            session.close();
            return;
        }
        
        Long assetId;
        
        try {
            assetId = Long.valueOf(params.get(TERMINAL_ASSET_ID));
        } catch (NumberFormatException e) {
            Map<String, Object> errorResponse = Map.of(
                TERMINAL_TYPE, TERMINAL_CONNECTION_ERROR,
                TERMINAL_MESSAGE, MSG_INVALID_NUMERIC_PARAMS
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
            session.close();
            return;
        }
        
        log.info("Terminal WebSocket connection established for asset ID: {}", assetId);
        
        // Send connection ready message - session will be created when AUTHENTICATE action is received
        Map<String, Object> response = Map.of(
            TERMINAL_TYPE, TERMINAL_TYPE_CONNECTION_READY,
            TERMINAL_MESSAGE, MSG_TERMINAL_CONNECTION_READY
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * Handle Unix group actions
     */
    private void handleUnixGroupAction(WebSocketSession session, String action, Map<String, Object> data) {
        switch (action) {
            case "authenticate":
                handleAuthentication(session, data);
                break;
            case "get_folder_suggestions":
                handleFolderSuggestions(session, data);
                break;
            case "apply_folder_permissions":
                handleApplyFolderPermissions(session, data);
                break;
            case "disconnect":
                handleTerminalDisconnect(session);
                break;
            default:
                log.warn("Unknown Unix group action: {}", action);
                sendErrorResponse(session, "Unknown action: " + action);
        }
    }

    /**
     * Handle terminal actions (existing logic)
     */
    private void handleTerminalAction(WebSocketSession session, String action, Map<String, Object> data) {
        switch (action) {
            case TERMINAL_ACTION_AUTHENTICATE:
                handleAuthentication(session, data);
                break;
            case TERMINAL_ACTION_RESIZE:
                handleTerminalResize(session, data);
                break;
            case TERMINAL_ACTION_KEYBOARD_EVENT:
                handleKeyboardEvent(session, data);
                break;
            case TERMINAL_ACTION_DISCONNECT:
                handleTerminalDisconnect(session);
                break;
            default:
                log.warn("Unknown terminal action: {}", action);
        }
    }

    // handleTerminalInput method removed - now using handleKeyboardEvent for all input
    
    private void handleTerminalResize(WebSocketSession session, Map<String, Object> data) {
        Integer cols = (Integer) data.get("cols");
        Integer rows = (Integer) data.get("rows");
        log.debug("Terminal resize request: {}x{}", cols, rows);
        
        // Get session metadata to find the terminal session
        Map<String, String> metadata = sessionMetadata.get(session.getId());
        if (metadata == null) {
            log.warn("No session metadata found for WebSocket session: {}", session.getId());
            return;
        }
        
        String sessionId = metadata.get(TERMINAL_SESSION_ID);
        TerminalSession terminalSession = terminalService.getSession(sessionId);
        
        if (terminalSession == null) {
            log.warn("Terminal session not found: {}", sessionId);
            return;
        }
        
        if (!terminalSession.isConnected()) {
            log.warn("SSH session not connected for session: {}", sessionId);
            return;
        }
        
        try {
            // Resize the SSH terminal via service
            terminalService.resizeTerminal(sessionId, cols, rows);
            
            Map<String, Object> response = Map.of(
                TERMINAL_TYPE, TERMINAL_TYPE_RESIZE_CONFIRMED,
                TERMINAL_COLS, cols,
                TERMINAL_ROWS, rows
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("Error resizing terminal", e);
        }
    }

    private void handleTerminalDisconnect(WebSocketSession session) {
        log.info("Terminal disconnect request");
        
        try {
            // Get the terminal session ID from metadata
            Map<String, String> metadata = sessionMetadata.get(session.getId());
            String terminalSessionId = metadata != null ? metadata.get(TERMINAL_SESSION_ID) : null;
            
            // Close the terminal session first
            closeTerminalSessionSafely(terminalSessionId);
            
            // Send confirmation response
            Map<String, Object> response = Map.of(
                TERMINAL_TYPE, TERMINAL_TYPE_DISCONNECT_CONFIRMED,
                TERMINAL_MESSAGE, MSG_TERMINAL_SESSION_CLOSED
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            session.close();
        } catch (Exception e) {
            log.error("Error handling disconnect", e);
        }
    }
    
    /**
     * Safely close a terminal session with proper error handling
     */
    private void closeTerminalSessionSafely(String terminalSessionId) {
        if (terminalSessionId != null) {
            try {
                terminalService.closeSession(terminalSessionId);
                log.info("Terminal session closed via disconnect request: {}", terminalSessionId);
            } catch (Exception e) {
                log.error("Error closing terminal session during disconnect: {}", terminalSessionId, e);
            }
        }
    }
    
    /**
     * Handle authentication message from frontend
     */
    private void handleAuthentication(WebSocketSession session, Map<String, Object> data) {
        try {
            String token = (String) data.get(TERMINAL_TOKEN);
            String authProviderStr = (String) data.get(TERMINAL_AUTH_PROVIDER);
            Long assetId = data.get(TERMINAL_ASSET_ID) != null ? Long.valueOf(data.get(TERMINAL_ASSET_ID).toString()) : null;
            String userAccessType = (String) data.get(Constants.WS_FIELD_USER_ACCESS_TYPE); // ASSET_OWNER or ACCESSOR
            
            log.info("Handling authentication for asset: {}, provider: {}, accessType: {}", assetId, authProviderStr, userAccessType);
            
            if (token == null || token.isEmpty()) {
                sendErrorResponse(session, MSG_AUTHENTICATION_TOKEN_REQUIRED);
                return;
            }
            
            if (userAccessType == null || userAccessType.isEmpty()) {
                sendErrorResponse(session, "userAccessType is required (ASSET_OWNER or ACCESSOR)");
                return;
            }
            
            // Validate userAccessType
            if (!Roles.ASSET_OWNER.getOriginalName().equals(userAccessType) && 
                !Roles.ACCESSOR.getOriginalName().equals(userAccessType)) {
                sendErrorResponse(session, "Invalid userAccessType. Must be ASSET_OWNER or ACCESSOR");
                return;
            }
            
            // Create terminal session with authenticated user
            createAuthenticatedTerminalSession(session, assetId, token, authProviderStr, userAccessType);
            
        } catch (Exception e) {
            log.error("Error handling authentication", e);
            sendErrorResponse(session, MSG_AUTHENTICATION_PROCESSING_ERROR + e.getMessage());
        }
    }
    
    
    
    /**
     * Send error response to client
     */
    private void sendErrorResponse(WebSocketSession session, String message) {
        try {
            // Get connection type to determine error response format
            Map<String, String> metadata = sessionMetadata.get(session.getId());
            String connectionType = metadata != null ? metadata.get(Constants.CONNECTION_TYPE_FIELD) : Constants.CONNECTION_TYPE_TERMINAL;
            
            Map<String, Object> errorResponse;
            if (Constants.CONNECTION_TYPE_UNIX_GROUPS.equals(connectionType)) {
                errorResponse = Map.of(
                    "type", Constants.WS_MESSAGE_TYPE_ERROR,
                    Constants.JSON_FIELD_MESSAGE, message
                );
            } else {
                errorResponse = Map.of(
                    TERMINAL_ACTION, TERMINAL_TYPE_ERROR,
                    TERMINAL_MESSAGE, message
                );
            }
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        } catch (Exception e) {
            log.error("Failed to send error response", e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        
        log.info("=== WebSocket connection closed ===");
        log.info(Constants.LOG_SESSION_ID, sessionId);
        log.info("Close Status: {} - {}", status.getCode(), status.getReason());
        
        // Get the terminal session ID from metadata before cleaning up
        Map<String, String> metadata = sessionMetadata.get(sessionId);
        String terminalSessionId = metadata != null ? metadata.get(TERMINAL_SESSION_ID) : null;
        
        // Clean up WebSocket session metadata
        sessions.remove(sessionId);
        sessionMetadata.remove(sessionId);
        
        // Close the terminal session if it exists and hasn't been closed already
        if (terminalSessionId != null) {
            try {
                // Check if the session is still active before trying to close it
                if (terminalService.getSession(terminalSessionId) != null) {
                    terminalService.closeSession(terminalSessionId);
                    log.info("Terminal session closed: {} (WebSocket: {})", terminalSessionId, sessionId);
                } else {
                    log.debug("Terminal session already closed: {} (WebSocket: {})", terminalSessionId, sessionId);
                }
            } catch (Exception e) {
                log.error("Error closing terminal session: {} (WebSocket: {})", terminalSessionId, sessionId, e);
            }
        }
        
        log.info("WebSocket connection cleanup completed for session: {}", sessionId);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("=== WebSocket transport error ===");
        log.error(Constants.LOG_SESSION_ID, session.getId());
        log.error("Error: ", exception);
        super.handleTransportError(session, exception);
    }
    
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new ConcurrentHashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }



    /**
     * Create authenticated terminal session - extracted from handleAuthentication
     */
    private void createAuthenticatedTerminalSession(WebSocketSession session, Long assetId, String token, String authProviderStr, String userAccessType) {
        try {
            AuthProvider authProvider = AuthProvider.valueOf(authProviderStr.toUpperCase());
            Map<String, String> metadata = getSessionMetadata(session);
            
            TerminalSession terminalSession = terminalService.createSessionWithToken(
                assetId, token, authProvider, 
                metadata.get(Constants.SESSION_METADATA_CLIENT_IP), 
                metadata.get(Constants.SESSION_METADATA_USER_AGENT),
                userAccessType
            );
            
            updateSessionMetadata(session, assetId, terminalSession, userAccessType);
            sendAuthenticationSuccessResponse(session, terminalSession);
            
            establishSSHConnectionAsync(session, terminalSession, metadata.get(Constants.CONNECTION_TYPE_FIELD));
            
        } catch (Exception e) {
            log.error("Authentication failed for asset: {}", assetId, e);
            sendErrorResponse(session, MSG_AUTHENTICATION_FAILED + e.getMessage());
        }
    }
    
    /**
     * Get session metadata with default values
     */
    private Map<String, String> getSessionMetadata(WebSocketSession session) {
        Map<String, String> metadata = sessionMetadata.get(session.getId());
        if (metadata == null) {
            metadata = new ConcurrentHashMap<>();
        }
        
        metadata.putIfAbsent(Constants.SESSION_METADATA_CLIENT_IP, Constants.UNKNOWN_VALUE);
        metadata.putIfAbsent(Constants.SESSION_METADATA_USER_AGENT, Constants.UNKNOWN_VALUE);
        metadata.putIfAbsent(Constants.CONNECTION_TYPE_FIELD, Constants.CONNECTION_TYPE_TERMINAL);
        
        return metadata;
    }
    
    /**
     * Update session metadata with terminal session information
     */
    private void updateSessionMetadata(WebSocketSession session, Long assetId, TerminalSession terminalSession, String userAccessType) {
        Map<String, String> existingMetadata = sessionMetadata.get(session.getId());
        if (existingMetadata != null) {
            existingMetadata.put(TERMINAL_ASSET_ID, assetId.toString());
            existingMetadata.put(TERMINAL_SESSION_ID, terminalSession.getSessionId());
            existingMetadata.put(Constants.WS_FIELD_USER_ACCESS_TYPE, userAccessType);
        } else {
            // Fallback if metadata doesn't exist (shouldn't happen)
            Map<String, String> newMetadata = new ConcurrentHashMap<>();
            newMetadata.put(TERMINAL_ASSET_ID, assetId.toString());
            newMetadata.put(TERMINAL_SESSION_ID, terminalSession.getSessionId());
            newMetadata.put(Constants.WS_FIELD_USER_ACCESS_TYPE, userAccessType);
            sessionMetadata.put(session.getId(), newMetadata);
        }
    }
    
    /**
     * Send authentication success response to client
     */
    private void sendAuthenticationSuccessResponse(WebSocketSession session, TerminalSession terminalSession) {
        try {
            Map<String, Object> response = Map.of(
                "type", Constants.WS_MESSAGE_TYPE_AUTHENTICATION_SUCCESS,
                Constants.WS_FIELD_SESSION_ID, terminalSession.getSessionId(),
                Constants.JSON_FIELD_MESSAGE, Constants.MSG_TERMINAL_SESSION_AUTHENTICATED
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            log.info("Authentication successful for asset: {}", terminalSession.getAssetId());
        } catch (Exception e) {
            log.error("Error sending authentication success response", e);
        }
    }
    
    /**
     * Establish SSH connection asynchronously
     */
    private void establishSSHConnectionAsync(WebSocketSession session, TerminalSession terminalSession, String connectionType) {
        new Thread(() -> {
            try {
                sendMessage(session, TERMINAL_TYPE_CONNECTION_PROGRESS, MSG_CONNECTING_TO_SSH, "");
                
                String sessionId = getTerminalSessionId(session, terminalSession);
                establishSSHConnectionWithCallback(session, sessionId, connectionType);
                
                // Send SSH connected message
                Map<String, Object> response = Map.of(
                    "type", Constants.WS_MESSAGE_TYPE_SSH_CONNECTED,
                    Constants.JSON_FIELD_MESSAGE, Constants.MSG_SSH_CONNECTION_ESTABLISHED
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                
            } catch (Exception e) {
                handleSSHConnectionError(session, e);
            }
        }).start();
    }
    
    /**
     * Get terminal session ID from metadata or fallback to terminal session
     */
    private String getTerminalSessionId(WebSocketSession session, TerminalSession terminalSession) {
        Map<String, String> sessionMeta = sessionMetadata.get(session.getId());
        return sessionMeta != null ? sessionMeta.get(TERMINAL_SESSION_ID) : terminalSession.getSessionId();
    }
    
    /**
     * Establish SSH connection with output callback
     */
    private void establishSSHConnectionWithCallback(WebSocketSession session, String sessionId, String connectionType) {
        try {
            terminalService.establishSSHConnection(sessionId, (output) -> {
                try {
                    log.debug("SSH output callback received: {}", output.replaceAll(SSH_REGEX_NEWLINE, SSH_NEWLINE_ESCAPE));
                    if (Constants.CONNECTION_TYPE_TERMINAL.equals(connectionType)) {
                        sendMessage(session, TERMINAL_TYPE_OUTPUT, "", output);
                    }
                } catch (Exception e) {
                    log.error("Error forwarding SSH output to WebSocket", e);
                    handleSSHConnectionError(session, e);
                }
            });
        } catch (Exception e) {
            log.error("Error establishing SSH connection", e);
            handleSSHConnectionError(session, e);
        }
    }
    
    /**
     * Handle SSH connection errors
     */
    private void handleSSHConnectionError(WebSocketSession session, Exception e) {
        log.error("Failed to establish SSH connection", e);
        try {
            // Send SSH connection failed message
            Map<String, Object> response = Map.of(
                "type", Constants.WS_MESSAGE_TYPE_SSH_CONNECTION_FAILED,
                Constants.JSON_FIELD_MESSAGE, Constants.MSG_FAILED_TO_ESTABLISH_SSH + e.getMessage()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception ex) {
            log.error("Error sending SSH error message", ex);
        }
    }
    
    
    /**
     * Send a message to the WebSocket client
     */
    private void sendMessage(WebSocketSession session, String type, String message, String data) {
        try {
            Map<String, Object> response = Map.of(
                TERMINAL_TYPE, type,
                TERMINAL_MESSAGE, message,
                TERMINAL_DATA, data
            );
            String jsonResponse = objectMapper.writeValueAsString(response);
            log.debug("Sending message to WebSocket client: {}", jsonResponse);
            session.sendMessage(new TextMessage(jsonResponse));
        } catch (Exception e) {
            log.error("Error sending message to WebSocket client", e);
        }
    }
    
    /**
     * Extract client IP address from WebSocket session
     */
    private String getClientIpFromWebSocketSession(WebSocketSession session) {
        try {
            // Try to get IP from session attributes first
            Object remoteAddress = session.getAttributes().get("remoteAddress");
            if (remoteAddress != null) {
                return remoteAddress.toString();
            }
            
            // Try to get from session URI host
            if (session.getUri() != null && session.getUri().getHost() != null) {
                return session.getUri().getHost();
            }
            
            // Try to get from session handshake headers
            Map<String, Object> attributes = session.getAttributes();
            if (attributes.containsKey("javax.servlet.request.X-Forwarded-For")) {
                String xForwardedFor = (String) attributes.get("javax.servlet.request.X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
            }
            
            if (attributes.containsKey("javax.servlet.request.X-Real-IP")) {
                return (String) attributes.get("javax.servlet.request.X-Real-IP");
            }
            
            if (attributes.containsKey("javax.servlet.request.remoteAddr")) {
                return (String) attributes.get("javax.servlet.request.remoteAddr");
            }
            
        } catch (Exception e) {
            log.debug("Could not extract client IP from WebSocket session", e);
        }
        
        return Constants.UNKNOWN_VALUE;
    }
    
    /**
     * Extract user agent from WebSocket session
     */
    private String getUserAgentFromWebSocketSession(WebSocketSession session) {
        try {
            // Try to get from session handshake headers
            Map<String, Object> attributes = session.getAttributes();
            if (attributes.containsKey("javax.servlet.request.userAgent")) {
                return (String) attributes.get("javax.servlet.request.userAgent");
            }
            
            // Try to get from session headers
            Map<String, List<String>> headers = session.getHandshakeHeaders();
            if (headers.containsKey("User-Agent")) {
                List<String> userAgents = headers.get("User-Agent");
                if (userAgents != null && !userAgents.isEmpty()) {
                    return userAgents.get(0);
                }
            }
            
        } catch (Exception e) {
            log.debug("Could not extract user agent from WebSocket session", e);
        }
        
        return Constants.UNKNOWN_VALUE;
    }
    
    /**
     * Handle keyboard events (including Tab completion)
     * Forward keyboard events directly to the SSH terminal for native handling
     */
    private void handleKeyboardEvent(WebSocketSession session, Map<String, Object> data) {
        try {
            // Get the terminal session ID from metadata
            Map<String, String> metadata = sessionMetadata.get(session.getId());
            String terminalSessionId = metadata != null ? metadata.get(TERMINAL_SESSION_ID) : null;
            
            if (terminalSessionId == null) {
                log.warn("No terminal session found for keyboard event");
                return;
            }
            
            // Get the keyboard event data
            String keyCode = (String) data.get("keyCode");
            String key = (String) data.get("key");

            log.info("🔍 Keyboard event received - key: [{}], keyCode: [{}]", key, keyCode);
            
            // Convert keyboard event to appropriate character/sequence
            String keySequence = convertKeyboardEventToSequence(key, keyCode);
            
            if (keySequence != null) {
                // Send the key sequence directly to the SSH terminal
                terminalService.sendKeyboardInput(terminalSessionId, keySequence);
                log.info("✅ Forwarded keyboard event to SSH terminal: [{}] (key: [{}], keyCode: [{}])", 
                        keySequence.replace("\t", "\\t").replace("\b", "\\b"), key, keyCode);
            } else {
                log.warn("❌ Unknown keyboard event - key: [{}], keyCode: [{}]", key, keyCode);
            }
            
        } catch (Exception e) {
            log.error("Error handling keyboard event", e);
        }
    }
    
    /**
     * Convert keyboard event to SSH terminal sequence
     */
    private String convertKeyboardEventToSequence(String key, String keyCode) {
        // Handle special keys
        String specialKey = handleSpecialKeys(key, keyCode);
        if (specialKey != null) {
            return specialKey;
        }
        
        // Handle arrow keys
        String arrowKey = handleArrowKeys(key, keyCode);
        if (arrowKey != null) {
            return arrowKey;
        }
        
        // Handle function keys
        String functionKey = handleFunctionKeys(key);
        if (functionKey != null) {
            return functionKey;
        }
        
        // For regular characters, use the key directly
        if (key != null && key.length() == 1) {
            return key;
        }
        
        return null; // Unknown key
    }
    
    /**
     * Handle special keys (Tab, Enter, Backspace, Delete, Escape)
     */
    private String handleSpecialKeys(String key, String keyCode) {
        if ("Tab".equals(key) || "9".equals(keyCode)) {
            return "\t"; // Tab character
        }
        if ("Enter".equals(key) || "13".equals(keyCode)) {
            return "\n"; // Newline
        }
        if ("Backspace".equals(key) || "8".equals(keyCode)) {
            return "\b"; // Backspace
        }
        if ("Delete".equals(key) || "46".equals(keyCode)) {
            return "\u007f"; // DEL character
        }
        if ("Escape".equals(key) || "27".equals(keyCode)) {
            return "\u001b"; // ESC character
        }
        return null;
    }
    
    /**
     * Handle arrow keys
     */
    private String handleArrowKeys(String key, String keyCode) {
        if ("ArrowUp".equals(key) || "38".equals(keyCode)) {
            return "\u001b[A"; // Up arrow
        }
        if ("ArrowDown".equals(key) || "40".equals(keyCode)) {
            return "\u001b[B"; // Down arrow
        }
        if ("ArrowRight".equals(key) || "39".equals(keyCode)) {
            return "\u001b[C"; // Right arrow
        }
        if ("ArrowLeft".equals(key) || "37".equals(keyCode)) {
            return "\u001b[D"; // Left arrow
        }
        return null;
    }
    
    /**
     * Handle function keys (F1-F12)
     */
    private String handleFunctionKeys(String key) {
        if (key != null && key.startsWith("F") && key.length() <= 3) {
            // F1-F12 keys - simplified handling
            return "\u001b[" + key.substring(1) + "~";
        }
        return null;
    }

    /**
     * Handle folder suggestions for terminal (reuse existing SSH connection)
     */
    private void handleFolderSuggestions(WebSocketSession session, Map<String, Object> data) {
        try {
            String path = (String) data.get("path");
            if (path == null || path.trim().isEmpty()) {
                path = "/";
            }

            // Get session metadata to find the asset ID and terminal session
            Map<String, String> metadata = sessionMetadata.get(session.getId());
            if (metadata == null) {
                sendErrorResponse(session, "Session not authenticated");
                return;
            }

            String assetIdStr = metadata.get(TERMINAL_ASSET_ID);
            if (assetIdStr == null) {
                sendErrorResponse(session, "Asset ID not found in session");
                return;
            }

            Long assetId = Long.valueOf(assetIdStr);
            Asset asset = assetService.findById(assetId);
            if (asset == null) {
                sendErrorResponse(session, "Asset not found: " + assetId);
                return;
            }

            log.debug("Getting terminal folder suggestions for asset: {} and path: {}", assetId, path);

            // Get terminal session ID from metadata for direct WebSocket SSH reuse
            String terminalSessionId = metadata.get(TERMINAL_SESSION_ID);
            log.debug("Using terminal session ID: {} for folder suggestions", terminalSessionId);

            // Get folder suggestions using existing WebSocket SSH connection with session ID
            List<UnixFolderSuggestion> suggestions = unixGroupService.getDetailedFolderSuggestions(asset, path, terminalSessionId);

            // Filter suggestions based on the typed path
            List<UnixFolderSuggestion> filteredSuggestions = filterSuggestionsByPath(suggestions, path);

            // Send suggestions back to client in terminal format
            Map<String, Object> response = Map.of(
                    "type", Constants.WS_MESSAGE_TYPE_FOLDER_SUGGESTIONS,
                    Constants.WS_FIELD_SUGGESTIONS, filteredSuggestions,
                    Constants.WS_FIELD_PATH, path,
                    Constants.WS_FIELD_ACTION, "get_folder_suggestions"
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            log.debug("Sent {} terminal folder suggestions for path: {}", filteredSuggestions.size(), path);

        } catch (Exception e) {
            log.error("Error getting terminal folder suggestions", e);
            sendErrorResponse(session, "Failed to get folder suggestions: " + e.getMessage());
        }
    }

    /**
     * Filter folder suggestions based on the typed path
     */
    private List<UnixFolderSuggestion> filterSuggestionsByPath(List<UnixFolderSuggestion> suggestions, String typedPath) {
        if (typedPath == null || typedPath.equals("/")) {
            // Return all root level suggestions
            return suggestions.stream()
                    .filter(s -> s.isDirectory())
                    .toList();
        }

        return suggestions.stream()
                .filter(s -> s.isDirectory())
                .filter(s -> {
                    return s.getPath().startsWith(typedPath.toLowerCase());
                })
                .limit(20) // Limit to 20 suggestions for performance
                .toList();
    }

    /**
     * Handle folder permissions application
     */
    private void handleApplyFolderPermissions(WebSocketSession session, Map<String, Object> data) {
        try {
            // Extract permission data
            String folderPath = (String) data.get("folderPath");
            Boolean readPermission = (Boolean) data.get("readPermission");
            Boolean writePermission = (Boolean) data.get("writePermission");
            Boolean executePermission = (Boolean) data.get("executePermission");
            Boolean recursive = (Boolean) data.get("recursive");
            Long groupId = data.get("groupId") != null ? Long.valueOf(data.get("groupId").toString()) : null;

            // Get asset ID from session metadata
            Map<String, String> metadata = sessionMetadata.get(session.getId());
            if (metadata == null) {
                sendErrorResponse(session, "Session not authenticated");
                return;
            }

            String assetIdStr = metadata.get(TERMINAL_ASSET_ID);
            if (assetIdStr == null) {
                sendErrorResponse(session, "Asset ID not found in session");
                return;
            }

            Long assetId = Long.valueOf(assetIdStr);

            // Validate required fields
            if (folderPath == null || groupId == null || readPermission == null || 
                writePermission == null || executePermission == null) {
                sendErrorResponse(session, "Missing required permission fields");
                return;
            }

            // Create folder access request
            FolderAccessRequestDTO request = new FolderAccessRequestDTO();
            request.setFolderPath(folderPath);
            request.setReadPermission(readPermission);
            request.setWritePermission(writePermission);
            request.setExecutePermission(executePermission);
            request.setRecursive(recursive != null && recursive);
            request.setGroupId(groupId);
            request.setAssetId(assetId);

            log.debug("Applying folder permissions: {}", request);

            // Apply permissions using existing WebSocket SSH connection
            unixGroupService.applyFolderAccessPermissions(request);

            // Send success response
            Map<String, Object> response = Map.of(
                    "type", Constants.WS_MESSAGE_TYPE_PERMISSION_APPLIED,
                    Constants.JSON_FIELD_MESSAGE, "Folder permissions applied successfully",
                    Constants.WS_FIELD_FOLDER_PATH, folderPath
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            log.info("Applied folder permissions for group {} on path {}", groupId, folderPath);

        } catch (Exception e) {
            log.error("Error applying folder permissions", e);
            sendErrorResponse(session, "Failed to apply folder permissions: " + e.getMessage());
        }
    }

}

