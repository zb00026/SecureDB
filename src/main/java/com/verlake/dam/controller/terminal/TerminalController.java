package com.verlake.dam.controller.terminal;

import com.verlake.dam.service.terminal.TerminalService;
import com.verlake.dam.entity.terminal.TerminalSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.verlake.dam.utils.Constants.*;
import com.verlake.dam.utils.Constants;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.verlake.dam.enums.AuthProvider;

@Component
@Slf4j
@RequiredArgsConstructor
public class TerminalController extends TextWebSocketHandler {
    
    private final TerminalService terminalService;
    private final ObjectMapper objectMapper;
    
    // Store WebSocket sessions mapped to terminal sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Store session metadata for each WebSocket session
    private final Map<String, Map<String, String>> sessionMetadata = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("=== WebSocket connection attempt ===");
        log.info("Session ID: {}", session.getId());
        log.info("URI: {}", session.getUri());
        log.info("Query: {}", session.getUri().getQuery());
        
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        // Extract parameters from query string
        String query = session.getUri().getQuery();
        Map<String, String> params = parseQueryString(query);
        
        // Get client IP address from WebSocket session
        String clientIp = getClientIpFromWebSocketSession(session);
        String userAgent = getUserAgentFromWebSocketSession(session);
        
        // Store client information in session metadata
        Map<String, String> metadata = new ConcurrentHashMap<>();
        metadata.put("clientIp", clientIp);
        metadata.put("userAgent", userAgent);
        sessionMetadata.put(sessionId, metadata);
        
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
        
        log.info("WebSocket connection established for asset ID: {}", assetId);
        
        // Send connection ready message - session will be created when AUTHENTICATE action is received
        Map<String, Object> response = Map.of(
            TERMINAL_TYPE, TERMINAL_TYPE_CONNECTION_READY,
            TERMINAL_MESSAGE, MSG_TERMINAL_CONNECTION_READY
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            
            // Check for action type
            String action = (String) data.get(TERMINAL_ACTION);
            if (action != null) {
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
                        log.warn("Unknown action: {}", action);
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            
            Map<String, Object> errorResponse = Map.of(
                TERMINAL_TYPE, TERMINAL_TYPE_ERROR,
                TERMINAL_MESSAGE, MSG_FAILED_TO_PROCESS_MESSAGE + e.getMessage()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
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
            
            log.info("Handling authentication for asset: {}, provider: {}", assetId, authProviderStr);
            
            if (token == null || token.isEmpty()) {
                sendErrorResponse(session, MSG_AUTHENTICATION_TOKEN_REQUIRED);
                return;
            }
            
            // Create terminal session with authenticated user
            createAuthenticatedTerminalSession(session, assetId, token, authProviderStr);
            
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
            Map<String, Object> errorResponse = Map.of(
                TERMINAL_ACTION, TERMINAL_TYPE_ERROR,
                TERMINAL_MESSAGE, message
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        } catch (Exception e) {
            log.error("Failed to send error response", e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        
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
        
        log.info("WebSocket connection closed: {} (Status: {})", sessionId, status);
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
    private void createAuthenticatedTerminalSession(WebSocketSession session, Long assetId, String token, String authProviderStr) {
        try {
            AuthProvider authProvider = AuthProvider.valueOf(authProviderStr.toUpperCase());
            
            // Get client information from session metadata
            Map<String, String> metadata = sessionMetadata.get(session.getId());
            String clientIp = metadata != null ? metadata.get("clientIp") : Constants.UNKNOWN_VALUE;
            String userAgent = metadata != null ? metadata.get("userAgent") : Constants.UNKNOWN_VALUE;
            
            TerminalSession terminalSession = terminalService.createSessionWithToken(assetId, token, authProvider, clientIp, userAgent);
            
            // Store session info for later use - THIS WAS MISSING!
            sessionMetadata.put(session.getId(), Map.of(
                TERMINAL_ASSET_ID, assetId.toString(),
                TERMINAL_SESSION_ID, terminalSession.getSessionId()
            ));
            
            // Send authentication success response
            Map<String, Object> response = Map.of(
                TERMINAL_ACTION, TERMINAL_ACTION_AUTHENTICATION_SUCCESS,
                TERMINAL_SESSION_ID, terminalSession.getSessionId(),
                TERMINAL_MESSAGE, MSG_TERMINAL_SESSION_AUTHENTICATED
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            log.info("Authentication successful for asset: {}", assetId);
            
            // Establish SSH connection asynchronously - THIS WAS ALSO MISSING!
            new Thread(() -> {
                try {
                    // Send connection progress message
                    sendMessage(session, TERMINAL_TYPE_CONNECTION_PROGRESS, MSG_CONNECTING_TO_SSH, "");
                    
                    // Get the correct session ID from metadata
                    Map<String, String> sessionMeta = sessionMetadata.get(session.getId());
                    String sessionId = sessionMeta != null ? sessionMeta.get(TERMINAL_SESSION_ID) : terminalSession.getSessionId();
                    
                    // Establish SSH connection with output callback that forwards to WebSocket
                    terminalService.establishSSHConnection(sessionId, (output) -> {
                        try {
                            log.debug("SSH output callback received: {}", output.replaceAll(SSH_REGEX_NEWLINE, SSH_NEWLINE_ESCAPE));
                            // Forward SSH output to WebSocket client
                            sendMessage(session, TERMINAL_TYPE_OUTPUT, "", output);
                        } catch (Exception e) {
                            log.error("Error forwarding SSH output to WebSocket", e);
                        }
                    });
                    
                    // Send connection success message
                    sendMessage(session, TERMINAL_TYPE_SSH_CONNECTED, MSG_SSH_CONNECTION_ESTABLISHED, "");
                    
                } catch (Exception e) {
                    log.error("Failed to establish SSH connection", e);
                    try {
                        sendMessage(session, TERMINAL_TYPE_SSH_ERROR, MSG_FAILED_TO_ESTABLISH_SSH + e.getMessage(), "");
                    } catch (Exception ex) {
                        log.error("Error sending SSH error message", ex);
                    }
                }
            }).start();
            
        } catch (Exception e) {
            log.error("Authentication failed for asset: {}", assetId, e);
            sendErrorResponse(session, MSG_AUTHENTICATION_FAILED + e.getMessage());
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
}

