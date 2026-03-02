package com.verlake.dam.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.ai.AIMaskingPolicy;
import com.verlake.dam.entity.ai.ChatMessage;
import com.verlake.dam.entity.ai.FieldSuggestion;
import com.verlake.dam.entity.ai.MaskingIntent;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.SensitiveCategory;
import com.verlake.dam.repository.assets.AssetRepository;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.AuditDescriptionUtils;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AIChatService {

    private final GeminiAIService geminiAIService;
    private final SchemaAnalysisService schemaAnalysisService;
    private final AIMaskingPolicyService maskingPolicyService;
    private final AssetRepository assetRepository;
    private final UserService userService;
    private final AuditTrailService auditTrailService;
    private final AIPromptService promptService;
    private final ObjectMapper objectMapper;

    // In-memory chat sessions (consider using Redis for production)
    private final Map<String, List<ChatMessage>> chatSessions = new ConcurrentHashMap<>();
    private final Map<String, ChatContext> sessionContexts = new ConcurrentHashMap<>();

    @Value("${ai.chat.session.timeout-minutes:60}")
    private int sessionTimeoutMinutes;

    @Value("${ai.chat.max.messages.per.session:50}")
    private int maxMessagesPerSession;

    @Value("${ai.chat.confidence.threshold:0.75}")
    private double confidenceThreshold;

    public AIChatService(GeminiAIService geminiAIService,
            SchemaAnalysisService schemaAnalysisService,
            AIMaskingPolicyService maskingPolicyService,
            AssetRepository assetRepository,
            UserService userService,
            AuditTrailService auditTrailService,
            AIPromptService promptService,
            ObjectMapper objectMapper) {
        this.geminiAIService = geminiAIService;
        this.schemaAnalysisService = schemaAnalysisService;
        this.maskingPolicyService = maskingPolicyService;
        this.assetRepository = assetRepository;
        this.userService = userService;
        this.auditTrailService = auditTrailService;
        this.promptService = promptService;
        this.objectMapper = objectMapper;
    }

    /**
     * Start a new chat session
     */
    public ChatMessage startChatSession(String sessionId, Long assetId) {
        try {
            // Get asset information
            Asset asset = assetRepository.findById(assetId)
                    .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

            // Create session context
            ChatContext context = new ChatContext();
            context.setAssetId(assetId);
            context.setAsset(asset);
            context.setUserId(getCurrentUserId());
            context.setUserEmail(getCurrentUserEmail());
            context.setStartTime(LocalDateTime.now());
            sessionContexts.put(sessionId, context);

            // Create welcome message from database prompt
            Map<String, Object> promptParams = Map.of("assetName", asset.getName());
            String welcomeContent = promptService.getPrompt("WELCOME_MESSAGE", promptParams);

            ChatMessage welcomeMessage = createMessage(sessionId, welcomeContent, Constants.AI_SENDER,
                    ChatMessage.MessageType.WELCOME);

            log.info("Started AI chat session {} for asset {} by user {}", sessionId, assetId, getCurrentUserEmail());

            return welcomeMessage;

        } catch (Exception e) {
            log.error("Error starting chat session: {}", e.getMessage());
            return createErrorMessage(sessionId, "I couldn't start the chat session. Please try again.");
        }
    }

    /**
     * Process user message and generate AI response
     */
    public ChatMessage processUserMessage(String sessionId, String userMessage) {
        try {
            // Validate session
            ChatContext context = sessionContexts.get(sessionId);
            if (context == null) {
                return createErrorMessage(sessionId, Constants.ERROR_SESSION_EXPIRED);
            }

            // Check session timeout
            if (isSessionExpired(context)) {
                sessionContexts.remove(sessionId);
                chatSessions.remove(sessionId);
                return createErrorMessage(sessionId, Constants.ERROR_SESSION_EXPIRED);
            }

            // Store user message
            ChatMessage userMsg = createMessage(sessionId, userMessage, context.getUserEmail(),
                    ChatMessage.MessageType.TEXT);
            userMsg.setAssetId(context.getAssetId().toString());

            // Analyze user intent
            MaskingIntent intent = geminiAIService.analyzeMaskingIntent(userMessage);
            log.info("Analyzed intent for session {}: {} (confidence: {})",
                    sessionId, intent.getIntentType(), intent.getConfidence());

            // Check if this is a location restriction error
            if (isLocationRestrictionError(intent)) {
                return createLocationRestrictionMessage(sessionId);
            }

            // Process suggestions based on intent
            List<FieldSuggestion> suggestions = processSuggestions(context, userMessage, intent);

            // Generate AI response
            ChatMessage aiResponse = generateAIResponse(sessionId, userMessage, intent, suggestions);

            // Store last understood intent and suggestions for quick confirm/apply
            context.setLastIntent(intent);
            context.setLastSuggestions(suggestions);

            // If user already confirmed in this message, apply immediately
            if (isAffirmative(userMessage) && intent.getConfidence() >= confidenceThreshold && !suggestions.isEmpty()) {
                return applyMaskingPolicy(sessionId, intent, suggestions);
            }
            return aiResponse;

        } catch (Exception e) {
            log.error("Error processing user message in session {}: {}", sessionId, e.getMessage());
            return createErrorMessage(sessionId, "I encountered an error processing your request. Please try again.");
        }
    }

    /**
     * Process suggestions based on user intent and confidence
     */
    private List<FieldSuggestion> processSuggestions(ChatContext context, String userMessage, MaskingIntent intent) {
        // Handle "remove X from the list" - take last suggestions and exclude specified table.field
        if (isRemoveFromListRequest(userMessage)) {
            List<String[]> toRemove = parseTableFieldToRemove(userMessage);
            if (!toRemove.isEmpty()) {
                if (context.getLastSuggestions() != null && !context.getLastSuggestions().isEmpty()) {
                    List<FieldSuggestion> filtered = filterOutFromSuggestions(context.getLastSuggestions(), toRemove);
                    log.info("Removed {} field(s) from list for session {}, {} remaining",
                            toRemove.size(), context.getAssetId(), filtered.size());
                    return filtered;
                }
                // No previous list - return empty to avoid misinterpretation (AI would show only the removed field)
                log.info("Remove requested but no previous suggestions for session {}", context.getAssetId());
                return new ArrayList<>();
            }
        }

        List<FieldSuggestion> suggestions = new ArrayList<>();

        // Get schema suggestions if high confidence
        if (intent.getConfidence() >= confidenceThreshold) {
            suggestions = schemaAnalysisService.analyzeAssetSchema(context.getAsset(), userMessage);
            log.info("Found {} field suggestions for session {}", suggestions.size(), context.getAssetId());
        }

        // Align and filter suggestions using the interpreted intent
        suggestions = adjustSuggestionsWithIntent(intent, suggestions);

        // Validate and hydrate suggestions from live schema
        suggestions = schemaAnalysisService.validateAndHydrateSuggestions(context.getAsset(), suggestions);

        // Apply category focus filtering
        return applyCategoryFocusFilter(intent, suggestions);
    }

    /**
     * Detect if user wants to remove a column from the current masking list
     */
    private boolean isRemoveFromListRequest(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return false;
        String lower = userMessage.toLowerCase();
        boolean hasRemoveKeyword = lower.contains("remove") || lower.contains("exclude") || lower.contains("drop")
                || lower.contains("don't mask") || lower.contains("do not mask") || lower.contains("unmask");
        boolean hasTableFieldPattern = Pattern.compile("\\w++\\.\\w++").matcher(userMessage).find();
        return hasRemoveKeyword && hasTableFieldPattern;
    }

    /**
     * Parse table.field patterns from user message (e.g. lab_results.test_name)
     */
    private List<String[]> parseTableFieldToRemove(String userMessage) {
        List<String[]> result = new ArrayList<>();
        Matcher m = Pattern.compile("\\b(\\w++)\\.(\\w++)\\b").matcher(userMessage);
        while (m.find()) {
            result.add(new String[]{m.group(1), m.group(2)});
        }
        return result;
    }

    /**
     * Filter out suggestions matching the specified table.field pairs
     */
    private List<FieldSuggestion> filterOutFromSuggestions(List<FieldSuggestion> suggestions, List<String[]> toRemove) {
        return suggestions.stream()
                .filter(s -> {
                    String tn = s.getTableName() != null ? s.getTableName().toLowerCase() : "";
                    String fn = s.getFieldName() != null ? s.getFieldName().toLowerCase() : "";
                    for (String[] pair : toRemove) {
                        String targetTable = pair[0].toLowerCase();
                        String targetField = pair[1].toLowerCase();
                        if ((tn.equals(targetTable) || tn.contains(targetTable) || targetTable.contains(tn))
                                && (fn.equals(targetField) || fn.contains(targetField) || targetField.contains(fn))) {
                            return false; // exclude this suggestion
                        }
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Apply category focus filtering based on intent
     */
    private List<FieldSuggestion> applyCategoryFocusFilter(MaskingIntent intent, List<FieldSuggestion> suggestions) {
        SensitiveCategory focusCategory = inferCategoryFromIntent(intent);
        if (focusCategory == null) {
            return suggestions;
        }

        List<FieldSuggestion> narrowed = new ArrayList<>();
        for (FieldSuggestion s : suggestions) {
            if (matchesCategoryFocus(s, focusCategory)) {
                narrowed.add(s);
            }
        }
        return narrowed;
    }

    /**
     * Check if suggestion matches the category focus
     */
    private boolean matchesCategoryFocus(FieldSuggestion s, SensitiveCategory focusCategory) {
        String fname = s.getFieldName() != null ? s.getFieldName().toLowerCase() : "";

        // Check if category matches
        if (s.getCategory() != null && s.getCategory() == focusCategory) {
            return true;
        }

        // Check name heuristics
        return switch (focusCategory) {
            case PHONE -> containsAnyIgnoreCase(fname, Constants.FIELD_KEYWORD_PHONE, Constants.FIELD_KEYWORD_MOBILE,
                    "tel", Constants.FIELD_KEYWORD_CONTACT);
            case EMAIL -> containsAnyIgnoreCase(fname, Constants.FIELD_KEYWORD_EMAIL, "mail");
            case SSN -> containsAnyIgnoreCase(fname, "ssn", "social_security", "social_sec");
            case CREDIT_CARD -> containsAnyIgnoreCase(fname, "credit_card", "card_number", "cc_number", "creditcard")
                    || fname.equals("cc");
            case PASSWORD -> containsAnyIgnoreCase(fname, "password", "pass", "pwd", "secret");
            case NAME -> containsAnyIgnoreCase(fname, "name", "fname", "lname", "first_name", "last_name", "full_name",
                    "username");
            case ADDRESS -> containsAnyIgnoreCase(fname, "address", "addr", "street", "city", "zip", "postal");
            case DATE_OF_BIRTH -> containsAnyIgnoreCase(fname, "birth", "dob", "birthday", "born");
            default -> false;
        };
    }

    /**
     * Audit masking application
     */
    private void auditMaskingApplication(MaskingIntent intent, List<AIMaskingPolicy> appliedPolicies, ChatContext context) {
        try {
        AuditTrail audit = AuditTrail.builder()
                    .timestamp(LocalDateTime.now())
                    .user(getCurrentUserEmail())
                    .action(Constants.AUDIT_ACTION_AI_MASKING_APPLIED)
                    .previousValue(intent.getOriginalRequest())
                    .newValue(objectMapper.writeValueAsString(appliedPolicies))
                    .asset(context.getAsset())
                    .description(AuditDescriptionUtils.generateDescription(Constants.AUDIT_ACTION_AI_MASKING_APPLIED, Constants.ENTITY_TYPE_ASSET, null))
                    // readableDescription will be computed at read-time (DTO)
                    .build();
            auditTrailService.save(audit);
        } catch (Exception ex) {
            log.warn("Failed to audit masking apply: {}", ex.getMessage());
        }
    }

    /**
     * Apply masking policy based on user confirmation
     */
    public ChatMessage applyMaskingPolicy(String sessionId, MaskingIntent intent,
            List<FieldSuggestion> confirmedSuggestions) {
        try {
            ChatContext context = sessionContexts.get(sessionId);
            if (context == null) {
                return createErrorMessage(sessionId, Constants.ERROR_SESSION_EXPIRED);
            }

            // Fallback to last suggestions if none provided
            if ((confirmedSuggestions == null || confirmedSuggestions.isEmpty())
                    && context.getLastSuggestions() != null) {
                confirmedSuggestions = context.getLastSuggestions();
            }

            // Ensure suggestions adhere to intent and are schema-valid before applying
            confirmedSuggestions = adjustSuggestionsWithIntent(intent, confirmedSuggestions);
            confirmedSuggestions = schemaAnalysisService.validateAndHydrateSuggestions(context.getAsset(),
                    confirmedSuggestions);

            // Apply the masking policies
            List<AIMaskingPolicy> appliedPolicies = maskingPolicyService.applyMaskingPolicies(
                    context.getAsset(), intent, confirmedSuggestions, getCurrentUser());

            // Create confirmation message using database prompt
            Map<String, Object> promptParams = Map.of(
                    "appliedCount", appliedPolicies.size(),
                    "appliedPolicies", formatAppliedPolicies(appliedPolicies),
                    Constants.PROMPT_PARAM_STRATEGY, intent.getMaskingStrategy(),
                    "userRole", intent.getUserRole(),
                    "confidence", String.format("%.1f", intent.getConfidence() * 100));
            String confirmationContent = promptService.getPrompt("POLICY_APPLIED_SUCCESS", promptParams);

            ChatMessage confirmation = createMessage(sessionId, confirmationContent, Constants.AI_SENDER,
                    ChatMessage.MessageType.POLICY_APPLIED);
            confirmation.setParsedIntent(intent);
            confirmation.setSuggestions(confirmedSuggestions);

            log.info("Applied {} masking policies for session {} on asset {}",
                    appliedPolicies.size(), sessionId, context.getAssetId());

            // Audit: masking applied
            auditMaskingApplication(intent, appliedPolicies, context);

            return confirmation;

        } catch (Exception e) {
            log.error("Error applying masking policy in session {}: {}", sessionId, e.getMessage());
            return createErrorMessage(sessionId, Constants.ERROR_FAILED_TO_APPLY_MASKING_POLICY);
        }
    }

    /**
     * Get chat session history
     */
    public List<ChatMessage> getChatHistory(String sessionId) {
        return chatSessions.getOrDefault(sessionId, new ArrayList<>());
    }

    private List<FieldSuggestion> adjustSuggestionsWithIntent(MaskingIntent intent, List<FieldSuggestion> suggestions) {
        if (suggestions == null)
            return new ArrayList<>();

        String text = buildIntentText(intent);
        boolean hasTargetTables = intent.getTargetTables() != null && !intent.getTargetTables().isEmpty();
        boolean hasTargetFields = intent.getTargetFields() != null && !intent.getTargetFields().isEmpty();
        boolean hasCategoryFocus = !text.isBlank();

        List<FieldSuggestion> filtered = filterSuggestionsByIntent(suggestions, intent, text, hasTargetTables,
                hasTargetFields);

        // Apply intent overrides to filtered suggestions
        applyIntentOverrides(filtered, intent);

        // Handle empty filtered results
        if (filtered.isEmpty() && (hasTargetTables || hasTargetFields || hasCategoryFocus)) {
            return filtered; // Force clarification flow
        }

        if (filtered.isEmpty()) {
            return applyOverridesToOriginal(suggestions, intent);
        }

        return filtered;
    }

    /**
     * Build intent text for filtering
     */
    private String buildIntentText(MaskingIntent intent) {
        return ((intent.getIntentType() != null ? intent.getIntentType() : "") + " " +
                (intent.getOriginalRequest() != null ? intent.getOriginalRequest() : "")).toLowerCase();
    }

    /**
     * Filter suggestions based on intent criteria
     */
    private List<FieldSuggestion> filterSuggestionsByIntent(List<FieldSuggestion> suggestions, MaskingIntent intent,
            String text, boolean hasTargetTables, boolean hasTargetFields) {
        List<FieldSuggestion> filtered = new ArrayList<>();

        for (FieldSuggestion s : suggestions) {
            if (shouldIncludeSuggestion(s, intent, text, hasTargetTables, hasTargetFields)) {
                filtered.add(s);
            }
        }

        return filtered;
    }

    /**
     * Check if suggestion should be included based on all filters
     */
    private boolean shouldIncludeSuggestion(FieldSuggestion s, MaskingIntent intent, String text,
            boolean hasTargetTables, boolean hasTargetFields) {
        return passesCategoryFilter(s, text) &&
                (!hasTargetTables || matchesTargetTables(s, intent.getTargetTables())) &&
                (!hasTargetFields || matchesTargetFields(s, intent.getTargetFields()));
    }

    /**
     * Check if suggestion passes category filter
     */
    private boolean passesCategoryFilter(FieldSuggestion s, String text) {
        if (text.isBlank()) {
            return true;
        }

        String fname = s.getFieldName() != null ? s.getFieldName().toLowerCase() : "";

        // Phone/contact focus
        if ((text.contains(Constants.FIELD_KEYWORD_PHONE) || text.contains(Constants.FIELD_KEYWORD_CONTACT)) &&
                !matchesPhoneCategory(s, fname)) {
            return false;
        }

        // Email focus
        if (text.contains(Constants.FIELD_KEYWORD_EMAIL) && s.getCategory() != null
                && s.getCategory() != SensitiveCategory.EMAIL) {
            return false;
        }

        // SSN focus
        if ((text.contains("ssn") || text.contains("social security")) &&
                s.getCategory() != null && s.getCategory() != SensitiveCategory.SSN) {
            return false;
        }

        // Credit card focus
        return !((text.contains("credit") || text.contains("card")) &&
                s.getCategory() != null && s.getCategory() != SensitiveCategory.CREDIT_CARD);
    }

    /**
     * Check if suggestion matches phone category
     */
    private boolean matchesPhoneCategory(FieldSuggestion s, String fname) {
        if (s.getCategory() != null && s.getCategory() == SensitiveCategory.PHONE) {
            return true;
        }
        return s.getCategory() == null &&
                (fname.contains(Constants.FIELD_KEYWORD_PHONE) || fname.contains(Constants.FIELD_KEYWORD_MOBILE)
                        || fname.contains("tel") || fname.contains(Constants.FIELD_KEYWORD_CONTACT));
    }

    /**
     * Check if suggestion matches target tables
     */
    private boolean matchesTargetTables(FieldSuggestion s, List<String> targetTables) {
        if (s.getTableName() == null)
            return false;
        String tn = s.getTableName().toLowerCase();
        return targetTables.stream()
                .anyMatch(t -> {
                    String tt = t.toLowerCase();
                    return tn.equals(tt) || tn.contains(tt) || tt.contains(tn);
                });
    }

    /**
     * Check if suggestion matches target fields
     */
    private boolean matchesTargetFields(FieldSuggestion s, List<String> targetFields) {
        if (s.getFieldName() == null)
            return false;
        String fn = s.getFieldName().toLowerCase();
        return targetFields.stream()
                .anyMatch(f -> {
                    String tf = f.toLowerCase();
                    return fn.equals(tf) || fn.contains(tf) || tf.contains(fn);
                });
    }

    /**
     * Apply intent overrides to suggestions
     */
    private void applyIntentOverrides(List<FieldSuggestion> suggestions, MaskingIntent intent) {
        for (FieldSuggestion s : suggestions) {
            // Override strategy if specified in intent
            if (intent.getMaskingStrategy() != null && !intent.getMaskingStrategy().isBlank()) {
                s.setSuggestedStrategy(intent.getMaskingStrategy());
            }

            // Carry over masking params from intent when present
            if (intent.getPreserveChars() != null) {
                s.setPreserveChars(intent.getPreserveChars());
            }
            if (intent.getMaskChar() != null && !intent.getMaskChar().isBlank()) {
                s.setMaskChar(intent.getMaskChar());
            }

            // Set default preserve chars for common strategies when missing
            setDefaultPreserveChars(s);

            // Set default mask char if missing
            if (s.getMaskChar() == null || s.getMaskChar().isBlank()) {
                s.setMaskChar("*");
            }
        }
    }

    /**
     * Set default preserve chars based on strategy and field name
     */
    private void setDefaultPreserveChars(FieldSuggestion s) {
        if (s.getPreserveChars() != null) {
            return;
        }

        String strategy = s.getSuggestedStrategy() != null ? s.getSuggestedStrategy().toLowerCase() : "";
        if (!strategy.equals(Constants.MASKING_STRATEGY_PARTIAL)) {
            return;
        }

        // Heuristic: for card/ssn/phone keep last 4; email keep 1 before @
        if (s.getFieldName() != null
                && containsAnyIgnoreCase(s.getFieldName().toLowerCase(), "card", "ssn", Constants.FIELD_KEYWORD_PHONE,
                        Constants.FIELD_KEYWORD_MOBILE, "tel", Constants.FIELD_KEYWORD_CONTACT)) {
            s.setPreserveChars(4);
        } else if (s.getFieldName() != null && s.getFieldName().toLowerCase().contains(Constants.FIELD_KEYWORD_EMAIL)) {
            s.setPreserveChars(1);
        } else {
            s.setPreserveChars(2);
        }
    }

    /**
     * Apply overrides to original suggestions when filtered is empty
     */
    private List<FieldSuggestion> applyOverridesToOriginal(List<FieldSuggestion> suggestions, MaskingIntent intent) {
        List<FieldSuggestion> filtered = new ArrayList<>();
        for (FieldSuggestion s : suggestions) {
            if (intent.getMaskingStrategy() != null && !intent.getMaskingStrategy().isBlank()) {
                s.setSuggestedStrategy(intent.getMaskingStrategy());
            }
            if (intent.getPreserveChars() != null)
                s.setPreserveChars(intent.getPreserveChars());
            if (intent.getMaskChar() != null && !intent.getMaskChar().isBlank())
                s.setMaskChar(intent.getMaskChar());
            if (s.getMaskChar() == null || s.getMaskChar().isBlank())
                s.setMaskChar("*");
            filtered.add(s);
        }
        return filtered;
    }

    /**
     * Get session context
     */
    public ChatContext getSessionContext(String sessionId) {
        return sessionContexts.get(sessionId);
    }

    /**
     * End chat session
     */
    public void endChatSession(String sessionId) {
        chatSessions.remove(sessionId);
        sessionContexts.remove(sessionId);
        log.info("Ended chat session {}", sessionId);
    }

    /**
     * Get available fields for the current asset (fallback when AI is not
     * available)
     */
    public ChatMessage getAvailableFields(String sessionId) {
        try {
            ChatContext context = sessionContexts.get(sessionId);
            if (context == null) {
                return createErrorMessage(sessionId, Constants.ERROR_SESSION_EXPIRED);
            }

            // Get schema information using rule-based detection
            List<FieldSuggestion> fieldSuggestions = schemaAnalysisService.analyzeAssetSchema(context.getAsset(),
                    "Show all available fields");

            // Format the suggestions for display
            StringBuilder schemaInfo = new StringBuilder();
            if (!fieldSuggestions.isEmpty()) {
                schemaInfo.append("**Available Tables and Fields:**\n\n");
                String currentTable = "";
                for (FieldSuggestion suggestion : fieldSuggestions) {
                    if (!suggestion.getTableName().equals(currentTable)) {
                        currentTable = suggestion.getTableName();
                        schemaInfo.append("**Table: ").append(currentTable).append("**\n");
                    }
                    schemaInfo.append("• ").append(suggestion.getFieldName())
                            .append(" (").append(suggestion.getDataType()).append(")")
                            .append(" - ").append(suggestion.getReason()).append("\n");
                }
            } else {
                schemaInfo.append("No schema information available. Please check your database connection.");
            }

            // Create a simple field listing using database prompt
            Map<String, Object> promptParams = Map.of("schemaInfo", schemaInfo.toString());
            String fieldsContent = promptService.getPrompt("SCHEMA_FIELDS_INFO", promptParams);

            return createMessage(sessionId, fieldsContent, Constants.AI_SENDER,
                    ChatMessage.MessageType.INTENT_ANALYSIS);

        } catch (Exception e) {
            log.error("Error getting available fields for session {}: {}", sessionId, e.getMessage());
            return createErrorMessage(sessionId, Constants.ERROR_FAILED_TO_RETRIEVE_SCHEMA_INFO);
        }
    }

    /**
     * Generate AI response based on intent and suggestions
     */
    private ChatMessage generateAIResponse(String sessionId, String userMessage, MaskingIntent intent,
            List<FieldSuggestion> suggestions) {

        // Check if this is a fallback intent (low confidence due to AI unavailability)
        boolean isFallbackIntent = intent.getConfidence() < 0.5 &&
                intent.getReasoning() != null &&
                intent.getReasoning().contains("AI service unavailability");

        if (intent.getConfidence() < confidenceThreshold && !isFallbackIntent) {
            // Low confidence - ask for clarification using database prompt
            Map<String, Object> promptParams = Map.of(Constants.PROMPT_PARAM_USER_MESSAGE, userMessage);
            String clarificationContent = promptService.getPrompt("CLARIFICATION_REQUEST", promptParams);

            ChatMessage clarification = createMessage(sessionId, clarificationContent, Constants.AI_SENDER,
                    ChatMessage.MessageType.CLARIFICATION);
            clarification.setParsedIntent(intent);
            return clarification;
        }

        if (isFallbackIntent) {
            // AI service unavailable - provide helpful guidance using database prompt
            Map<String, Object> promptParams = Map.of(Constants.PROMPT_PARAM_USER_MESSAGE, userMessage);
            String fallbackContent = promptService.getPrompt("AI_FALLBACK_GUIDANCE", promptParams);

            ChatMessage fallback = createMessage(sessionId, fallbackContent, Constants.AI_SENDER,
                    ChatMessage.MessageType.FALLBACK);
            fallback.setParsedIntent(intent);
            return fallback;
        }

        if (suggestions.isEmpty()) {
            // Remove-from-list with no prior context - explain that a previous list is needed
            if (Constants.AI_INTENT_TYPE_REMOVE_FIELD.equals(intent.getIntentType())
                    || isRemoveFromListRequest(userMessage)) {
                String removeNoContextContent = "I don't have a previous list to remove from. "
                        + "Please first ask me to show or suggest fields to mask (e.g. \"Mask PII\" or \"Show sensitive fields\"), "
                        + "then you can remove specific columns from that list.";
                ChatMessage removeMsg = createMessage(sessionId, removeNoContextContent, Constants.AI_SENDER,
                        ChatMessage.MessageType.CLARIFICATION);
                removeMsg.setParsedIntent(intent);
                return removeMsg;
            }
            // No fields found - explain and suggest using database prompt
            Map<String, Object> promptParams = Map.of(
                    "originalRequest", intent.getOriginalRequest(),
                    "intentType", intent.getIntentType() != null ? intent.getIntentType().replace("_", " ") : "unknown",
                    Constants.PROMPT_PARAM_STRATEGY, intent.getMaskingStrategy() != null ? intent.getMaskingStrategy() : "",
                    "userRole", intent.getUserRole() != null ? intent.getUserRole() : "");
            String noFieldsContent = promptService.getPrompt("NO_FIELDS_FOUND", promptParams);

            ChatMessage noFields = createMessage(sessionId, noFieldsContent, Constants.AI_SENDER,
                    ChatMessage.MessageType.INTENT_ANALYSIS);
            noFields.setParsedIntent(intent);
            return noFields;
        }

        // Good confidence and fields found - show suggestions using database prompt
        Map<String, Object> promptParams = Map.of(
                Constants.PROMPT_PARAM_USER_MESSAGE, userMessage,
                "fieldSuggestions", formatFieldSuggestions(suggestions),
                Constants.PROMPT_PARAM_STRATEGY, intent.getMaskingStrategy(),
                "strategyDescription", getMaskingStrategyDescription(intent.getMaskingStrategy()));
        String suggestionsContent = promptService.getPrompt("FIELD_SUGGESTIONS_CHAT", promptParams);

        ChatMessage suggestionsMsg = createMessage(sessionId, suggestionsContent, Constants.AI_SENDER,
                ChatMessage.MessageType.FIELD_SUGGESTIONS);
        suggestionsMsg.setParsedIntent(intent);
        suggestionsMsg.setSuggestions(suggestions);
        suggestionsMsg.setRequiresUserAction(true);
        suggestionsMsg.setActionType(Constants.ACTION_TYPE_CONFIRM);

        return suggestionsMsg;
    }

    /**
     * Create a chat message
     */
    private ChatMessage createMessage(String sessionId, String content, String sender, ChatMessage.MessageType type) {
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .content(content)
                .sender(sender)
                .type(type)
                .timestamp(LocalDateTime.now())
                .userId(getCurrentUserId())
                .userEmail(getCurrentUserEmail())
                .build();

        // Add to session
        List<ChatMessage> session = chatSessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // Check max messages limit
        if (session.size() >= maxMessagesPerSession) {
            session.remove(0); // Remove oldest message
        }

        session.add(message);
        return message;
    }

    /**
     * Create error message
     */
    private ChatMessage createErrorMessage(String sessionId, String errorContent) {
        return createMessage(sessionId, "❌ " + errorContent, Constants.AI_SENDER, ChatMessage.MessageType.ERROR);
    }

    /**
     * Format field suggestions for display
     */
    private String formatFieldSuggestions(List<FieldSuggestion> suggestions) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < suggestions.size() && i < 10; i++) { // Limit to top 10
            FieldSuggestion suggestion = suggestions.get(i);
            sb.append(String.format("• **%s.%s** (%s) - %s (%.0f%% confidence)\n",
                    suggestion.getTableName(),
                    suggestion.getFieldName(),
                    suggestion.getDataType(),
                    suggestion.getReason(),
                    suggestion.getConfidence() * 100));
        }

        if (suggestions.size() > 10) {
            sb.append(String.format("• ... and %d more fields", suggestions.size() - 10));
        }

        return sb.toString();
    }

    /**
     * Format applied policies for display
     */
    private String formatAppliedPolicies(List<AIMaskingPolicy> policies) {
        StringBuilder sb = new StringBuilder();

        for (AIMaskingPolicy policy : policies) {
            sb.append(String.format("• **%s.%s** → %s masking\n",
                    policy.getTableName(),
                    policy.getFieldName(),
                    policy.getMaskingStrategy()));
        }

        return sb.toString();
    }

    /**
     * Get masking strategy description
     */
    private String getMaskingStrategyDescription(String strategy) {
        return switch (strategy.toLowerCase()) {
            case Constants.MASKING_STRATEGY_PARTIAL -> "Shows some characters, hides others (e.g., j***@example.com)";
            case Constants.MASKING_STRATEGY_FULL -> "Completely hides the data (e.g., *********)";
            case Constants.MASKING_STRATEGY_HASH -> "Replaces with irreversible hash values";
            case Constants.MASKING_STRATEGY_TOKENIZE -> "Replaces with secure tokens";
            default -> "Custom masking based on your requirements";
        };
    }

    /**
     * Check if session has expired
     */
    private boolean isSessionExpired(ChatContext context) {
        return context.getStartTime().plusMinutes(sessionTimeoutMinutes).isBefore(LocalDateTime.now());
    }

    /**
     * Get current user ID
     */
    private String getCurrentUserId() {
        try {
            return CommonUtils.getKeycloakUserIdFromSession();
        } catch (Exception e) {
            log.warn("Could not get current user ID: {}", e.getMessage());
            return Constants.UNKNOWN_USER;
        }
    }

    /**
     * Get current user email
     */
    private String getCurrentUserEmail() {
        try {
            return CommonUtils.getEmailFromSession();
        } catch (Exception e) {
            log.warn(Constants.ERROR_COULD_NOT_GET_CURRENT_USER_EMAIL, e.getMessage());
            return Constants.UNKNOWN_EMAIL;
        }
    }

    /**
     * Get current user
     */
    private User getCurrentUser() {
        try {
            String email = getCurrentUserEmail();
            return userService.findByEmail(email);
        } catch (Exception e) {
            log.warn("Could not get current user: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Chat context holder
     */
    private static class ChatContext {
        private Long assetId;
        private Asset asset;
        private String userId;
        private String userEmail;
        private LocalDateTime startTime;
        private MaskingIntent lastIntent;
        private List<FieldSuggestion> lastSuggestions;

        // Getters and setters
        public Long getAssetId() {
            return assetId;
        }

        public void setAssetId(Long assetId) {
            this.assetId = assetId;
        }

        public Asset getAsset() {
            return asset;
        }

        public void setAsset(Asset asset) {
            this.asset = asset;
        }

        @SuppressWarnings("unused")
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        @SuppressWarnings("unused")
        public String getUserEmail() {
            return userEmail;
        }

        public void setUserEmail(String userEmail) {
            this.userEmail = userEmail;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        @SuppressWarnings("unused")
        public MaskingIntent getLastIntent() {
            return lastIntent;
        }

        public void setLastIntent(MaskingIntent lastIntent) {
            this.lastIntent = lastIntent;
        }

        public List<FieldSuggestion> getLastSuggestions() {
            return lastSuggestions;
        }

        public void setLastSuggestions(List<FieldSuggestion> lastSuggestions) {
            this.lastSuggestions = lastSuggestions;
        }
    }

    private boolean isAffirmative(String message) {
        if (message == null)
            return false;
        String m = message.trim().toLowerCase();
        String[] phrases = { "yes", "apply", "apply it", "confirm", "go ahead", "proceed", "do it", "okay", "ok", "yep",
                "sure" };
        for (String p : phrases) {
            if (m.equals(p) || m.contains(p))
                return true;
        }
        return false;
    }

    private SensitiveCategory inferCategoryFromIntent(MaskingIntent intent) {
        if (intent == null)
            return null;

        String text = buildIntentText(intent);

        return getCategoryFromText(text);
    }

    /**
     * Get category from text content
     */
    private SensitiveCategory getCategoryFromText(String text) {
        if (text.contains("ssn") || text.contains("social security")) {
            return SensitiveCategory.SSN;
        }
        if (text.contains("phone") || text.contains("contact")) {
            return SensitiveCategory.PHONE;
        }
        if (text.contains("email") || text.contains("mail")) {
            return SensitiveCategory.EMAIL;
        }
        if (text.contains("credit") || text.contains("card") || text.contains("cc")) {
            return SensitiveCategory.CREDIT_CARD;
        }
        if (text.contains("password") || text.contains("pwd")) {
            return SensitiveCategory.PASSWORD;
        }
        if (text.contains("name")) {
            return SensitiveCategory.NAME;
        }
        if (text.contains("address")) {
            return SensitiveCategory.ADDRESS;
        }
        if (text.contains("dob") || text.contains("birth")) {
            return SensitiveCategory.DATE_OF_BIRTH;
        }
        return null;
    }

    /**
     * Safe alternative to regex matching - checks if field contains any of the
     * keywords
     */
    private boolean containsAnyIgnoreCase(String field, String... keywords) {
        if (field == null || field.isEmpty())
            return false;
        String lowerField = field.toLowerCase();
        for (String keyword : keywords) {
            if (lowerField.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the intent indicates a location restriction error
     */
    private boolean isLocationRestrictionError(MaskingIntent intent) {
        if (intent == null || intent.getReasoning() == null) {
            return false;
        }

        String reasoning = intent.getReasoning().toLowerCase();
        return reasoning.contains("not available in your region") ||
                reasoning.contains("location restriction") ||
                reasoning.contains("region not supported");
    }

    /**
     * Create a location restriction error message
     */
    private ChatMessage createLocationRestrictionMessage(String sessionId) {
        ChatMessage message = createMessage(sessionId,
                Constants.getMessage(Constants.GEMINI_LOCATION_RESTRICTION_RESPONSE_KEY),
                Constants.AI_SENDER, ChatMessage.MessageType.ERROR);
        message.setRequiresUserAction(false);
        return message;
    }
}