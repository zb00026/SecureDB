package com.verlake.dam.entity.ai;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    private String id;
    private String sessionId;
    private String content;
    private String sender; // "user", "ai", "system"
    private MessageType type;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    // AI-specific fields
    private MaskingIntent parsedIntent;
    private List<FieldSuggestion> suggestions;
    private String aiConfidence;
    private boolean requiresUserAction;
    private String actionType; // "confirm", "clarify", "apply"
    
    // Metadata
    private String assetId;
    private String userId;
    private String userEmail;
    
    public enum MessageType {
        TEXT,                    // Regular text message
        INTENT_ANALYSIS,        // AI analyzed user intent
        FIELD_SUGGESTIONS,      // AI suggests fields to mask
        POLICY_PREVIEW,         // Preview of policy to be applied
        CONFIRMATION_REQUEST,   // AI asks for confirmation
        POLICY_APPLIED,         // Confirmation that policy was applied
        ERROR,                  // Error message
        CLARIFICATION,          // AI asks for clarification
        WELCOME,                // Welcome/intro message
        FALLBACK                // AI service unavailable fallback
    }
} 