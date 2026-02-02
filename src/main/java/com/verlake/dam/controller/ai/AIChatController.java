package com.verlake.dam.controller.ai;


import com.verlake.dam.entity.ai.ApplyPolicyRequest;
import com.verlake.dam.entity.ai.ChatMessage;
import com.verlake.dam.entity.ai.ChatMessageRequest;
import com.verlake.dam.exception.AIChatException;
import com.verlake.dam.service.ai.AIChatService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/chat")
@Slf4j
public class AIChatController {

    @Autowired
    private AIChatService aiChatService;

    /**
     * Start a new chat session
     */
    @PostMapping("/session/start/{assetId}")
    public ResponseEntity<ChatMessage> startChatSession(@PathVariable Long assetId) {
        try {
            String sessionId = generateSessionId();
            
            ChatMessage welcomeMessage = aiChatService.startChatSession(sessionId, assetId);
            
            return ResponseEntity.ok(welcomeMessage);
        } catch (IllegalArgumentException e) {
            log.error("Invalid asset ID provided: {}", assetId, e);
            return ResponseEntity.badRequest()
                .body(ChatMessage.builder()
                    .sessionId(Constants.ERROR_SESSION_ID)
                    .content("Asset not found. Please provide a valid asset ID.")
                    .sender(Constants.ERROR_SYSTEM_SENDER)
                    .type(ChatMessage.MessageType.ERROR)
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (AIChatException e) {
            log.error("AI Chat error starting chat session for asset {}: {}", assetId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ChatMessage.builder()
                    .sessionId(Constants.ERROR_SESSION_ID)
                    .content(Constants.ERROR_FAILED_TO_START_CHAT_SESSION)
                    .sender(Constants.ERROR_SYSTEM_SENDER)
                    .type(ChatMessage.MessageType.ERROR)
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Unexpected error starting chat session for asset {}: {}", assetId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ChatMessage.builder()
                    .sessionId(Constants.ERROR_SESSION_ID)
                    .content(Constants.ERROR_FAILED_TO_START_CHAT_SESSION)
                    .sender(Constants.ERROR_SYSTEM_SENDER)
                    .type(ChatMessage.MessageType.ERROR)
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Send a message to the AI chat
     */
    @PostMapping("/message")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody ChatMessageRequest request) {
        try {
            ChatMessage response = aiChatService.processUserMessage(request.getSessionId(), request.getMessage());
            return ResponseEntity.ok(response);
            
        } catch (AIChatException e) {
            log.error("AI Chat error processing message for session {}: {}", request.getSessionId(), e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error processing message for session {}: {}", request.getSessionId(), e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get chat history
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(@PathVariable String sessionId) {
        try {
            List<ChatMessage> history = aiChatService.getChatHistory(sessionId);
            return ResponseEntity.ok(history);
        } catch (AIChatException e) {
            log.error("AI Chat error getting chat history for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error getting chat history for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Apply masking policy
     */
    @PostMapping("/apply-policy")
    public ResponseEntity<ChatMessage> applyMaskingPolicy(@RequestBody ApplyPolicyRequest request) {
        try {
            ChatMessage response = aiChatService.applyMaskingPolicy(
                request.getSessionId(), 
                request.getIntent(), 
                request.getSuggestions()
            );
            return ResponseEntity.ok(response);
            
        } catch (AIChatException e) {
            log.error("AI Chat error applying masking policy for session {}: {}", request.getSessionId(), e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error applying masking policy for session {}: {}", request.getSessionId(), e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * End chat session
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> endChatSession(@PathVariable String sessionId) {
        try {
            aiChatService.endChatSession(sessionId);
            return CommonUtils.getSuccessResponse();
        } catch (AIChatException e) {
            log.error("AI Chat error ending chat session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error ending chat session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, "Failed to end chat session"));
        }
    }

    /**
     * Get available fields for the current asset
     */
    @GetMapping("/fields/{sessionId}")
    public ResponseEntity<ChatMessage> getAvailableFields(@PathVariable String sessionId) {
        try {
            ChatMessage response = aiChatService.getAvailableFields(sessionId);
            return ResponseEntity.ok(response);
        } catch (AIChatException e) {
            log.error("AI Chat error getting available fields for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error getting available fields for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get AI suggestions
     */
    @GetMapping("/suggestions")
    public ResponseEntity<String> getSuggestions() {
        try {
            String suggestions = """
                Here are some common data masking requests you can try:
                
                • "Mask all email addresses in the customer table"
                • "Hide social security numbers in employee data"
                • "Partially mask phone numbers in contact list"
                • "Tokenize credit card numbers in payment table"
                • "Protect customer names and addresses"
                • "Mask sensitive data for non-admin users"
                • "Hide salary information in HR database"
                • "Protect patient medical records"
                
                Just type your request in natural language and I'll help you create the right masking policies!
                """;
            
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Unexpected error getting suggestions: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate a unique session ID
     */
    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + 
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    // removed: legacy converter (now using ObjectMapper in apply-policy)
} 