package com.verlake.dam.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.ai.MaskingIntent;
import com.verlake.dam.entity.ai.FieldSuggestion;
import com.verlake.dam.exception.AIPromptException;
import com.verlake.dam.exception.AIGeminiException;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class GeminiAIService {
    
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;
    private final float temperature;
    private final int topK;
    private final float topP;
    private final int maxRetries;
    private final int baseDelayMs;
    private final int maxDelayMs;
    private final ObjectMapper objectMapper;
    private final AIPromptService promptService;
    private final AISensitivePatternService patternService;
    
    // Circuit breaker state
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenTime = 0;
    private static final long CIRCUIT_TIMEOUT_MS = 60000; // 1 minute
    
    // Track last error type for better retry handling
    private volatile String lastErrorType = null;
    
    // Empty response tracking for Gemini 2.5 Flash
    private final AtomicInteger consecutiveEmptyResponses = new AtomicInteger(0);
    private static final int MAX_EMPTY_RESPONSES = 3;
    
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    
    @Autowired
    public GeminiAIService(RestTemplate restTemplate, String geminiApiKey, String geminiModelName,
                          int geminiMaxTokens, float geminiTemperature, int geminiTopK, float geminiTopP,
                          int geminiMaxRetries, int geminiBaseDelayMs, int geminiMaxDelayMs,
                          AIPromptService promptService, AISensitivePatternService patternService) {
        this.restTemplate = restTemplate;
        this.apiKey = geminiApiKey;
        this.modelName = geminiModelName;
        this.maxTokens = geminiMaxTokens;
        this.temperature = geminiTemperature;
        this.topK = geminiTopK;
        this.topP = geminiTopP;
        this.maxRetries = geminiMaxRetries;
        this.baseDelayMs = geminiBaseDelayMs;
        this.maxDelayMs = geminiMaxDelayMs;
        this.objectMapper = new ObjectMapper();
        this.promptService = promptService;
        this.patternService = patternService;
    }
    
    /**
     * Generate response from Gemini AI with retry logic
     */
    public String generateResponse(String prompt) {
        if (isCircuitBreakerOpen()) {
            return Constants.getMessage(Constants.GEMINI_CIRCUIT_BREAKER_FALLBACK_KEY);
        }
        
        return executeWithRetry(prompt);
    }
    
    /**
     * Check if circuit breaker is open
     */
    private boolean isCircuitBreakerOpen() {
        if (!circuitOpen) {
            return false;
        }
        
        long timeSinceOpen = System.currentTimeMillis() - circuitOpenTime;
        if (timeSinceOpen < CIRCUIT_TIMEOUT_MS) {
            log.warn("Circuit breaker is open, returning fallback response");
            return true;
        } else {
            log.info("Circuit breaker timeout reached, attempting to close");
            circuitOpen = false;
            return false;
        }
    }
    

    
    /**
     * Execute API call with retry logic
     */
    private String executeWithRetry(String prompt) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return executeApiCall(prompt);
            } catch (Exception e) {
                String result = handleRetryException(e, attempt);
                if (result != null) {
                    return result;
                }
                // Continue to next attempt for retryable errors
            }
        }
        
        return Constants.getMessage(Constants.GEMINI_GENERIC_ERROR_RESPONSE_KEY);
    }
    
    /**
     * Handle exception during retry
     */
    private String handleRetryException(Exception e, int attempt) {
        // Track error type for better retry handling
        lastErrorType = classifyErrorType(e);
        log.error("Error calling Gemini AI (attempt {}/{}): {} - Error type: {}", 
                 attempt + 1, maxRetries + 1, e.getMessage(), lastErrorType);
        
        if (isLocationRestrictionError(e)) {
            return Constants.getMessage(Constants.GEMINI_LOCATION_RESTRICTION_RESPONSE_KEY);
        }
        
        if (isRetryableError(e) && attempt < maxRetries) {
            handleRetryableError(attempt, e);
            return null; // Continue to next attempt
        }
        
        return handleFinalRetryFailure();
    }
    
    /**
     * Handle final retry failure
     */
    private String handleFinalRetryFailure() {
        openCircuitBreaker();
        
        // Return specific message for model overload
        if ("MODEL_OVERLOADED".equals(lastErrorType)) {
            return Constants.getMessage(Constants.GEMINI_MODEL_OVERLOADED_RESPONSE_KEY);
        }
        
        return Constants.getMessage(Constants.GEMINI_GENERIC_ERROR_RESPONSE_KEY);
    }
    
    /**
     * Execute a single API call
     */
    private String executeApiCall(String prompt) {
        String url = GEMINI_API_URL + modelName + ":generateContent?key=" + apiKey;
        Map<String, Object> requestBody = createRequestBody(prompt);
        
        log.debug("Sending request to Gemini API - URL: {}, Model: {}, MaxTokens: {}", 
                 url.replace(apiKey, "***"), modelName, maxTokens);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            url, HttpMethod.POST, request, String.class);
        
        if (circuitOpen) {
            log.info("Circuit breaker closed due to successful request");
            circuitOpen = false;
        }
        
        return extractTextFromResponse(response.getBody());
    }
    
    /**
     * Check if error is a location restriction
     */
    private boolean isLocationRestrictionError(Exception e) {
        return e.getMessage().contains("User location is not supported") || 
               e.getMessage().contains("FAILED_PRECONDITION");
    }
    

    
    /**
     * Check if error is retryable
     */
    private boolean isRetryableError(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("500") || 
               message.contains("internal") || 
               message.contains(Constants.GEMINI_ERROR_INTERNAL_ERROR) ||
               message.contains(Constants.GEMINI_ERROR_MODEL_OVERLOADED) ||
               message.contains(Constants.GEMINI_ERROR_QUOTA_EXCEEDED) ||
               message.contains(Constants.GEMINI_ERROR_RATE_LIMIT) ||
               message.contains(Constants.GEMINI_ERROR_TEMPORARILY_UNAVAILABLE) ||
               message.contains(Constants.GEMINI_ERROR_SERVICE_UNAVAILABLE);
    }
    
    /**
     * Handle retryable error with exponential backoff
     */
    private void handleRetryableError(int attempt, Exception e) {
        long delayMs = Math.min(baseDelayMs * (long) Math.pow(2, attempt), maxDelayMs);
        
        // Use longer delays for model overload errors
        if (isModelOverloadedError(e)) {
            delayMs = Math.min(delayMs * 2, (long) maxDelayMs * 2); // Double the delay for overload
            log.warn("Model overloaded, using extended delay: {} ms (attempt {}/{})", 
                    delayMs, attempt + 1, maxRetries + 1);
        } else {
            log.info("Retrying in {} ms due to retryable error (attempt {}/{})", 
                    delayMs, attempt + 1, maxRetries + 1);
        }
        
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry interrupted");
        }
    }
    
    /**
     * Check if the error is a model overload error
     */
    private boolean isModelOverloadedError(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains(Constants.GEMINI_ERROR_MODEL_OVERLOADED) ||
               message.contains(Constants.GEMINI_ERROR_QUOTA_EXCEEDED) ||
               message.contains(Constants.GEMINI_ERROR_RATE_LIMIT);
    }
    
    /**
     * Classify error type for better handling
     */
    private String classifyErrorType(Exception e) {
        String message = e.getMessage().toLowerCase();
        
        if (message.contains(Constants.GEMINI_ERROR_MODEL_OVERLOADED)) {
            return "MODEL_OVERLOADED";
        } else if (message.contains(Constants.GEMINI_ERROR_QUOTA_EXCEEDED)) {
            return "QUOTA_EXCEEDED";
        } else if (message.contains(Constants.GEMINI_ERROR_RATE_LIMIT)) {
            return "RATE_LIMIT";
        } else if (message.contains("500") || message.contains("internal")) {
            return "INTERNAL_ERROR";
        } else if (message.contains("location") || message.contains("precondition")) {
            return "LOCATION_RESTRICTION";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Open circuit breaker
     */
    private void openCircuitBreaker() {
        log.error("All retry attempts failed for Gemini AI call, opening circuit breaker");
        circuitOpen = true;
        circuitOpenTime = System.currentTimeMillis();
    }
    

    
    /**
     * Analyze masking intent from natural language with retry logic for empty responses
     */
    public MaskingIntent analyzeMaskingIntent(String userRequest) {
        Map<String, Object> promptParams = Map.of("userRequest", userRequest);
        String prompt = promptService.getPrompt("INTENT_ANALYSIS", promptParams);
        
        int maxAttempts = 2; // Try twice for intent analysis
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            MaskingIntent intent = processIntentAttempt(userRequest, prompt, attempt, maxAttempts);
            if (intent != null) {
                return intent;
            }
        }
        
        return createEnhancedFallbackIntent(userRequest);
    }
    
    /**
     * Process a single intent analysis attempt
     */
    private MaskingIntent processIntentAttempt(String userRequest, String prompt, int attempt, int maxAttempts) {
        try {
            String response = generateResponse(prompt);
            
            if (isAiServiceNotAvailable(response)) {
                log.warn("AI service not available in current region, using location restriction fallback intent");
                return createLocationRestrictionFallbackIntent(userRequest);
            }
            
            response = handleEmptyResponse(response, userRequest, attempt, maxAttempts);
            if (isEmptyResponse(response)) {
                log.warn("All attempts failed, creating enhanced fallback intent");
                return createEnhancedFallbackIntent(userRequest);
            }
            
            MaskingIntent intent = parseIntentResponse(response);
            
            if (intent.getConfidence() == 0.0 && attempt < maxAttempts - 1) {
                log.warn("Parsed intent has 0.0 confidence on attempt {}, retrying", attempt + 1);
                return null; // Continue to next attempt
            }
            
            return intent;
            
        } catch (Exception e) {
            log.error("Error parsing masking intent on attempt {}: {}", attempt + 1, e.getMessage());
            if (attempt == maxAttempts - 1) {
                return createEnhancedFallbackIntent(userRequest);
            }
            return null; // Continue to next attempt
        }
    }
    
    /**
     * Handle empty response with retry logic
     */
    private String handleEmptyResponse(String response, String userRequest, int attempt, int maxAttempts) {
        if (isEmptyResponse(response) && attempt < maxAttempts - 1) {
            return retryWithSimplifiedPrompt(userRequest, attempt);
        }
        return response;
    }
    
    /**
     * Check if AI service is not available
     */
    private boolean isAiServiceNotAvailable(String response) {
        return response.contains(Constants.GEMINI_ERROR_NOT_AVAILABLE_IN_REGION) || 
               response.contains(Constants.GEMINI_ERROR_MANUAL_MASKING_POLICY);
    }
    
    /**
     * Check if response is empty
     */
    private boolean isEmptyResponse(String response) {
        return response.contains("couldn't generate a response") || 
               response.contains("empty or unreadable response") ||
               response.contains("content policies or model limitations");
    }
    
    /**
     * Retry with simplified prompt
     */
    private String retryWithSimplifiedPrompt(String userRequest, int attempt) {
        log.warn("Gemini 2.5 Flash returned empty response on attempt {}, retrying with simplified prompt", attempt + 1);
        
        String simplifiedPrompt = String.format(
            "Parse this request and return JSON with intentType, maskingStrategy, confidence, targetFields, targetTables, userRole, reasoning:%n%n\"%s\"",
            userRequest
        );
        
        String retryResponse = generateResponse(simplifiedPrompt);
        if (!isEmptyResponse(retryResponse)) {
            return retryResponse;
        }
        return "";
    }
    
    /**
     * Parse intent response
     */
    private MaskingIntent parseIntentResponse(String response) throws Exception {
        String jsonResponse = extractJsonFromResponse(response);
        return objectMapper.readValue(jsonResponse, MaskingIntent.class);
    }
    
    /**
     * Suggest masking policies based on schema analysis
     */
    public List<FieldSuggestion> suggestMaskingPolicies(String schemaInfo, String userContext) {
        Map<String, Object> promptParams = Map.of(
            "schemaInfo", schemaInfo,
            "userContext", userContext
        );
        String prompt = promptService.getPrompt("FIELD_SUGGESTIONS", promptParams);
        
        try {
            String response = generateResponse(prompt);
            
            if (isAiServiceNotAvailable(response)) {
                log.warn("AI service not available in current region, using rule-based suggestions");
                return createRuleBasedSuggestions(schemaInfo);
            }
            
            return parseFieldSuggestions(response);
        } catch (Exception e) {
            log.error("Error parsing field suggestions: {}", e.getMessage());
            return createRuleBasedSuggestions(schemaInfo);
        }
    }
    
    /**
     * Parse field suggestions from response
     */
    private List<FieldSuggestion> parseFieldSuggestions(String response) {
        String jsonResponse = extractJsonFromResponse(response);
        
        try {
            return objectMapper.readValue(jsonResponse, new TypeReference<List<FieldSuggestion>>() {});
        } catch (Exception arrayException) {
            log.debug("Failed to parse as array, trying as single object: {}", arrayException.getMessage());
            return parseAsSingleSuggestion(jsonResponse);
        }
    }
    
    /**
     * Parse as single suggestion
     */
    private List<FieldSuggestion> parseAsSingleSuggestion(String jsonResponse) {
        try {
            FieldSuggestion singleSuggestion = objectMapper.readValue(jsonResponse, FieldSuggestion.class);
            return List.of(singleSuggestion);
        } catch (Exception singleException) {
            log.error("Failed to parse as single object: {}", singleException.getMessage());
            throw new AIGeminiException("Could not parse AI response as array or single object", singleException);
        }
    }
    
    /**
     * Generate conversational response for chat interface
     */
    public String generateChatResponse(String userMessage, MaskingIntent intent, List<FieldSuggestion> suggestions) {
        Map<String, Object> promptParams = Map.of(
            "userMessage", userMessage,
            "intentType", intent.getIntentType(),
            "confidence", String.format("%.2f", intent.getConfidence()),
            "targetFields", intent.getTargetFields() != null ? intent.getTargetFields().toString() : "[]",
            "maskingStrategy", intent.getMaskingStrategy(),
            "reasoning", intent.getReasoning() != null ? intent.getReasoning() : "",
            "suggestions", suggestions != null ? suggestions.toString() : "[]"
        );
        String prompt = promptService.getPrompt("CHAT_RESPONSE", promptParams);
        
        try {
            String response = generateResponse(prompt);
            
            if (isAiServiceNotAvailable(response)) {
                return createFallbackChatResponse(intent, suggestions);
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error generating chat response: {}", e.getMessage());
            return createFallbackChatResponse(intent, suggestions);
        }
    }
    
    /**
     * Create request body for Gemini API
     */
    private Map<String, Object> createRequestBody(String prompt) {
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put(Constants.GEMINI_JSON_FIELD_PARTS, Arrays.asList(part));
        
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("topK", topK);
        generationConfig.put("topP", topP);
        generationConfig.put("maxOutputTokens", maxTokens);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", Arrays.asList(content));
        requestBody.put("generationConfig", generationConfig);
        
        return requestBody;
    }
    
    /**
     * Extract text from Gemini API response
     */
    private String extractTextFromResponse(String responseBody) {
        try {
            log.debug("Extracting text from response: {}", responseBody);
            JsonNode root = objectMapper.readTree(responseBody);
            
            if (hasError(root)) {
                return handleError(root);
            }
            
            JsonNode candidates = root.get(Constants.GEMINI_JSON_FIELD_CANDIDATES);
            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                return extractTextFromCandidate(candidates.get(0));
            }
            
            return extractTextFromRoot(root, responseBody);
            
        } catch (Exception e) {
            log.error("Error parsing Gemini API response: {}", e.getMessage(), e);
            throw new AIPromptException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if response has error
     */
    private boolean hasError(JsonNode root) {
        return root.get(Constants.ERROR_FIELD_ERROR) != null;
    }
    
    /**
     * Handle error in response
     */
    private String handleError(JsonNode root) {
        JsonNode error = root.get(Constants.ERROR_FIELD_ERROR);
        JsonNode message = error.get("message");
        if (message != null) {
            log.error("Gemini API error: {}", message.asText());
            return "I encountered an error: " + message.asText() + ". Please try again.";
        }
        return Constants.getMessage(Constants.GEMINI_GENERIC_ERROR_RESPONSE_KEY);
    }
    
    /**
     * Extract text from candidate
     */
    private String extractTextFromCandidate(JsonNode firstCandidate) {
        if (hasFinishReason(firstCandidate)) {
            String finishReasonResponse = handleFinishReason(firstCandidate);
            if (finishReasonResponse != null) {
                return finishReasonResponse;
            }
        }
        
        JsonNode content = firstCandidate.get(Constants.GEMINI_JSON_FIELD_CONTENT);
        if (content != null) {
            String contentText = extractTextFromContent(content);
            if (contentText != null) {
                return contentText;
            }
        }
        
        return extractTextFromCandidateAlternative(firstCandidate);
    }
    
    /**
     * Check if candidate has finish reason
     */
    private boolean hasFinishReason(JsonNode candidate) {
        return candidate.get(Constants.GEMINI_JSON_FIELD_FINISH_REASON) != null;
    }
    
    /**
     * Handle finish reason
     */
    private String handleFinishReason(JsonNode candidate) {
        JsonNode finishReason = candidate.get(Constants.GEMINI_JSON_FIELD_FINISH_REASON);
        String reason = finishReason.asText();
        log.debug("Finish reason: {}", reason);
        
        switch (reason) {
            case "SAFETY":
                log.warn("Response blocked due to safety concerns");
                return "I couldn't process that request due to safety concerns. Please rephrase your request.";
            case "RECITATION":
                log.warn("Response blocked due to recitation concerns");
                return "I couldn't process that request due to content policy. Please rephrase your request.";
            case "LENGTH":
                log.warn("Response truncated due to length");
                return null; // Continue to extract what we have
            case "STOP", "OTHER":
                log.warn("Response finished with '{}' reason", reason);
                return null; // Continue to extract content
            default:
                log.warn("Unknown finish reason: {}", reason);
                return null;
        }
    }
    
    /**
     * Extract text from content
     */
    private String extractTextFromContent(JsonNode content) {
        // Check for direct text field
        JsonNode directText = content.get("text");
        if (directText != null && !directText.asText().trim().isEmpty()) {
            log.debug("Found direct text in content");
            return directText.asText();
        }
        
        // Check for parts array
        JsonNode parts = content.get(Constants.GEMINI_JSON_FIELD_PARTS);
        if (parts != null && parts.isArray() && parts.size() > 0) {
            return extractTextFromParts(parts);
        }
        
        // Check for empty response with role=model
        if (hasEmptyModelResponse(content, parts)) {
            return handleEmptyModelResponse();
        }
        
        log.warn("Content found but no extractable text. Content structure: {}", content.toString());
        return null;
    }
    
    /**
     * Extract text from parts array
     */
    private String extractTextFromParts(JsonNode parts) {
        for (int i = 0; i < parts.size(); i++) {
            JsonNode part = parts.get(i);
            JsonNode text = part.get("text");
            if (text != null && !text.asText().trim().isEmpty()) {
                log.debug("Found text in parts[{}]", i);
                
                // Reset empty response counter on successful response
                if (consecutiveEmptyResponses.get() > 0) {
                    log.debug("Resetting empty response counter after successful response");
                    consecutiveEmptyResponses.set(0);
                }
                
                return text.asText();
            }
        }
        return null;
    }
    
    /**
     * Check for empty model response
     */
    private boolean hasEmptyModelResponse(JsonNode content, JsonNode parts) {
        JsonNode role = content.get("role");
        return role != null && role.asText().equals("model") && (parts == null || parts.size() == 0);
    }
    
    /**
     * Handle empty model response
     */
    private String handleEmptyModelResponse() {
        log.warn("Gemini 2.5 Flash returned empty content with role=model but no parts");
        
        // Track consecutive empty responses
        int currentCount = consecutiveEmptyResponses.incrementAndGet();
        
        if (currentCount >= MAX_EMPTY_RESPONSES) {
            log.warn("Gemini 2.5 Flash has {} consecutive empty responses, possible model issues", currentCount);
        }
        
        return "I received your request but couldn't generate a response. This might be due to content policies or model limitations. Please try rephrasing your request or use manual policy creation.";
    }
    
    /**
     * Extract text from candidate alternative formats
     */
    private String extractTextFromCandidateAlternative(JsonNode candidate) {
        JsonNode candidateText = candidate.get("text");
        if (candidateText != null && !candidateText.asText().trim().isEmpty()) {
            log.debug("Found direct text in candidate");
            return candidateText.asText();
        }
        
        log.warn("No content found in candidate. Candidate structure: {}", candidate.toString());
        return null;
    }
    
    /**
     * Extract text from root response
     */
    private String extractTextFromRoot(JsonNode root, String responseBody) {
        JsonNode responseText = root.get("text");
        if (responseText != null && !responseText.asText().trim().isEmpty()) {
            log.debug("Found direct text in root response");
            return responseText.asText();
        }
        
        log.warn("No candidates found in response. Response structure: {}", root.toString());
        log.warn("Unable to extract text from Gemini 2.5 Flash response. Full response: {}", responseBody);
        return "I received your request but the AI service returned an empty or unreadable response. This might be due to content policies, model limitations, or a format change. Please try rephrasing your request or use manual policy creation.";
    }
    
    /**
     * Extract JSON from AI response (handles cases where AI adds extra text)
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }
        
        String json = extractJsonObject(response);
        if (json != null) {
            return json;
        }
        
        json = extractJsonArray(response);
        if (json != null) {
            return json;
        }
        
        return extractJsonFromLines(response);
    }
    
    /**
     * Extract JSON object from response
     */
    private String extractJsonObject(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}') + 1;
        
        if (start >= 0 && end > start) {
            String json = response.substring(start, end);
            log.debug("Extracted JSON object: {}", json);
            
            if (isMalformedArray(json)) {
                log.debug("Detected malformed array, wrapping in brackets");
                return "[" + json + "]";
            }
            
            return json;
        }
        return null;
    }
    
    /**
     * Check if JSON looks like a malformed array
     */
    private boolean isMalformedArray(String json) {
        return json.contains("},\n    {") || json.contains("},\n        {");
    }
    
    /**
     * Extract JSON array from response
     */
    private String extractJsonArray(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']') + 1;
        
        if (start >= 0 && end > start) {
            String json = response.substring(start, end);
            log.debug("Extracted JSON array: {}", json);
            return json;
        }
        return null;
    }
    
    /**
     * Extract JSON from individual lines
     */
    private String extractJsonFromLines(String response) {
        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (isJsonObject(line)) {
                log.debug("Found JSON in line: {}", line);
                return line;
            }
            if (isJsonArray(line)) {
                log.debug("Found JSON array in line: {}", line);
                return line;
            }
        }
        
        log.warn("No valid JSON found in response: {}", response);
        return "{}";
    }
    
    /**
     * Check if line is a JSON object
     */
    private boolean isJsonObject(String line) {
        return line.startsWith("{") && line.endsWith("}");
    }
    
    /**
     * Check if line is a JSON array
     */
    private boolean isJsonArray(String line) {
        return line.startsWith("[") && line.endsWith("]");
    }
    
    /**
     * Create fallback intent when AI parsing fails
     */
    private MaskingIntent createFallbackIntent(String userRequest) {
        return MaskingIntent.builder()
            .intentType(Constants.AI_INTENT_TYPE_CUSTOM)
            .targetFields(new ArrayList<>())
            .targetTables(new ArrayList<>())
            .maskingStrategy(Constants.GEMINI_STRATEGY_PARTIAL)
            .userRole("all")
            .confidence(0.3) // Low confidence to indicate AI service issues
            .originalRequest(userRequest)
            .reasoning("Could not parse request - manual configuration needed")
            .requiresConfirmation(true)
            .hasAmbiguity(true)
            .clarificationNeeded("Could you please rephrase your masking requirements?")
            .build();
    }
    
    /**
     * Create fallback intent specifically for location restriction errors
     */
    private MaskingIntent createLocationRestrictionFallbackIntent(String userRequest) {
        return MaskingIntent.builder()
            .intentType(Constants.AI_INTENT_TYPE_CUSTOM)
            .targetFields(new ArrayList<>())
            .targetTables(new ArrayList<>())
            .maskingStrategy(Constants.GEMINI_STRATEGY_PARTIAL)
            .userRole("all")
            .confidence(0.05) // Very low confidence to indicate AI service unavailability
            .originalRequest(userRequest)
            .reasoning("AI service not available in your region - manual configuration needed")
            .requiresConfirmation(true)
            .hasAmbiguity(true)
            .clarificationNeeded("Could you please rephrase your masking requirements?")
            .build();
    }
    
    /**
     * Create enhanced fallback intent with better confidence based on keyword analysis
     */
    private MaskingIntent createEnhancedFallbackIntent(String userRequest) {
        if (userRequest == null || userRequest.trim().isEmpty()) {
            return createFallbackIntent(userRequest);
        }
        
        String lowerRequest = userRequest.toLowerCase();
        
        // Analyze request using keyword matching to determine likely intent
        IntentAnalysis analysis = analyzeIntentFromKeywords(lowerRequest);
        
        // Cap confidence at reasonable level for fallback
        double confidence = Math.min(analysis.confidence, 0.8);
        
        return MaskingIntent.builder()
            .intentType(analysis.intentType)
            .targetFields(analysis.targetFields)
            .targetTables(analysis.targetTables)
            .maskingStrategy(analysis.strategy)
            .userRole("all")
            .confidence(confidence)
            .originalRequest(userRequest)
            .reasoning("Analyzed using keyword matching due to AI service limitations")
            .requiresConfirmation(true)
            .hasAmbiguity(confidence < 0.6)
            .clarificationNeeded(confidence < 0.6 ? "I analyzed your request using keyword matching. Please confirm if this matches your intent." : null)
            .build();
    }
    
    /**
     * Analyze intent from keywords
     */
    private IntentAnalysis analyzeIntentFromKeywords(String lowerRequest) {
        IntentAnalysis analysis = new IntentAnalysis();
        analysis.intentType = Constants.AI_INTENT_TYPE_CUSTOM;
        analysis.strategy = Constants.GEMINI_STRATEGY_PARTIAL;
        analysis.confidence = 0.4;
        
        analyzeIntentType(lowerRequest, analysis);
        analyzeStrategy(lowerRequest, analysis);
        analyzeTables(lowerRequest, analysis);
        
        return analysis;
    }
    
    /**
     * Analyze intent type from keywords
     */
    private void analyzeIntentType(String lowerRequest, IntentAnalysis analysis) {
        if (lowerRequest.contains("mask") || lowerRequest.contains("hide") || lowerRequest.contains("protect")) {
            analysis.intentType = "mask_data";
            analysis.confidence += 0.2;
        } else if (lowerRequest.contains("email")) {
            analysis.intentType = "mask_email";
            analysis.confidence += 0.3;
            analysis.targetFields.add("email");
        } else if (lowerRequest.contains("phone") || lowerRequest.contains("contact")) {
            analysis.intentType = "mask_phone";
            analysis.confidence += 0.3;
            analysis.targetFields.add("phone");
        } else if (lowerRequest.contains("ssn") || lowerRequest.contains("social")) {
            analysis.intentType = "mask_ssn";
            analysis.confidence += 0.3;
            analysis.targetFields.add("ssn");
        } else if (lowerRequest.contains("credit") || lowerRequest.contains("card")) {
            analysis.intentType = "mask_credit_card";
            analysis.confidence += 0.3;
            analysis.targetFields.add("credit_card");
        } else if (lowerRequest.contains("password") || lowerRequest.contains("pwd")) {
            analysis.intentType = "mask_password";
            analysis.confidence += 0.3;
            analysis.targetFields.add("password");
        }
    }
    
    /**
     * Analyze strategy from keywords
     */
    private void analyzeStrategy(String lowerRequest, IntentAnalysis analysis) {
        if (lowerRequest.contains("completely") || lowerRequest.contains("fully") || lowerRequest.contains("all")) {
            analysis.strategy = "full";
            analysis.confidence += 0.1;
        } else if (lowerRequest.contains("hash")) {
            analysis.strategy = "hash";
            analysis.confidence += 0.1;
        } else if (lowerRequest.contains("partial") || lowerRequest.contains("some")) {
            analysis.strategy = Constants.GEMINI_STRATEGY_PARTIAL;
            analysis.confidence += 0.1;
        }
    }
    
    /**
     * Analyze tables from keywords
     */
    private void analyzeTables(String lowerRequest, IntentAnalysis analysis) {
        String[] commonTables = {"users", "customers", "employees", "contacts", "accounts", "profiles"};
        for (String table : commonTables) {
            if (lowerRequest.contains(table)) {
                analysis.targetTables.add(table);
                analysis.confidence += 0.1;
                break;
            }
        }
    }
    
    /**
     * Helper class for intent analysis
     */
    private static class IntentAnalysis {
        String intentType;
        String strategy;
        double confidence;
        List<String> targetFields = new ArrayList<>();
        List<String> targetTables = new ArrayList<>();
    }
    
    /**
     * Create rule-based field suggestions when AI is not available
     * Uses database patterns for detection
     */
    private List<FieldSuggestion> createRuleBasedSuggestions(String schemaInfo) {
        // Use database patterns for rule-based suggestions
        return patternService.generateFieldSuggestions(schemaInfo);
    }
    
    /**
     * Test Gemini API connection with a simple prompt
     */
    public String testConnection() {
        try {
            String testPrompt = "Hello, please respond with 'OK' if you can see this message.";
            String response = generateResponse(testPrompt);
            
            if (response != null && response.contains("OK")) {
                return "Connection successful";
            } else {
                return "Connection failed - Response: " + response;
            }
        } catch (Exception e) {
            return "Connection failed - Error: " + e.getMessage();
        }
    }
    
    /**
     * Diagnose Gemini 2.5 Flash response issues
     */
    public String diagnoseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            StringBuilder diagnosis = new StringBuilder();
            
            diagnosis.append("=== Gemini 2.5 Flash Response Diagnosis ===\n");
            
            diagnoseCandidates(root, diagnosis);
            diagnoseErrors(root, diagnosis);
            diagnoseUsageMetadata(root, diagnosis);
            
            diagnosis.append("=====================================");
            
            return diagnosis.toString();
        } catch (Exception e) {
            return "Failed to diagnose response: " + e.getMessage();
        }
    }
    
    /**
     * Diagnose candidates structure
     */
    private void diagnoseCandidates(JsonNode root, StringBuilder diagnosis) {
        if (root.has(Constants.GEMINI_JSON_FIELD_CANDIDATES)) {
            JsonNode candidates = root.get(Constants.GEMINI_JSON_FIELD_CANDIDATES);
            diagnosis.append("✓ Has candidates array (").append(candidates.size()).append(" items)\n");
            
            if (candidates.size() > 0) {
                diagnoseFirstCandidate(candidates.get(0), diagnosis);
            }
        } else {
            diagnosis.append("✗ No candidates array found\n");
        }
    }
    
    /**
     * Diagnose first candidate
     */
    private void diagnoseFirstCandidate(JsonNode firstCandidate, StringBuilder diagnosis) {
        diagnoseFinishReason(firstCandidate, diagnosis);
        diagnoseContent(firstCandidate, diagnosis);
    }
    
    /**
     * Diagnose finish reason
     */
    private void diagnoseFinishReason(JsonNode candidate, StringBuilder diagnosis) {
        if (candidate.has(Constants.GEMINI_JSON_FIELD_FINISH_REASON)) {
            String reason = candidate.get(Constants.GEMINI_JSON_FIELD_FINISH_REASON).asText();
            diagnosis.append("✓ Finish reason: ").append(reason).append("\n");
        } else {
            diagnosis.append("✗ No finish reason found\n");
        }
    }
    
    /**
     * Diagnose content structure
     */
    private void diagnoseContent(JsonNode candidate, StringBuilder diagnosis) {
        if (candidate.has(Constants.GEMINI_JSON_FIELD_CONTENT)) {
            JsonNode content = candidate.get(Constants.GEMINI_JSON_FIELD_CONTENT);
            diagnosis.append("✓ Has content object\n");
            
            diagnoseContentRole(content, diagnosis);
            diagnoseContentParts(content, diagnosis);
            diagnoseContentText(content, diagnosis);
        } else {
            diagnosis.append("✗ No content object found\n");
        }
    }
    
    /**
     * Diagnose content role
     */
    private void diagnoseContentRole(JsonNode content, StringBuilder diagnosis) {
        if (content.has("role")) {
            diagnosis.append("  - Role: ").append(content.get("role").asText()).append("\n");
        }
    }
    
    /**
     * Diagnose content parts
     */
    private void diagnoseContentParts(JsonNode content, StringBuilder diagnosis) {
        if (content.has(Constants.GEMINI_JSON_FIELD_PARTS)) {
            JsonNode parts = content.get(Constants.GEMINI_JSON_FIELD_PARTS);
            diagnosis.append("  - Parts array: ").append(parts.size()).append(" items\n");
            
            for (int i = 0; i < parts.size(); i++) {
                JsonNode part = parts.get(i);
                if (part.has("text")) {
                    String text = part.get("text").asText();
                    diagnosis.append("    Part ").append(i).append(": '")
                            .append(text.length() > 50 ? text.substring(0, 50) + "..." : text)
                            .append("'\n");
                } else {
                    diagnosis.append("    Part ").append(i).append(": No text field\n");
                }
            }
        } else {
            diagnosis.append("  - ✗ No parts array found\n");
        }
    }
    
    /**
     * Diagnose content text
     */
    private void diagnoseContentText(JsonNode content, StringBuilder diagnosis) {
        if (content.has("text")) {
            diagnosis.append("  - ✓ Direct text field found\n");
        } else {
            diagnosis.append("  - ✗ No direct text field\n");
        }
    }
    
    /**
     * Diagnose errors
     */
    private void diagnoseErrors(JsonNode root, StringBuilder diagnosis) {
        if (root.has(Constants.ERROR_FIELD_ERROR)) {
            JsonNode error = root.get(Constants.ERROR_FIELD_ERROR);
            diagnosis.append("✗ Error present: ").append(error.toString()).append("\n");
        } else {
            diagnosis.append("✓ No error field\n");
        }
    }
    
    /**
     * Diagnose usage metadata
     */
    private void diagnoseUsageMetadata(JsonNode root, StringBuilder diagnosis) {
        if (root.has("usageMetadata")) {
            JsonNode usage = root.get("usageMetadata");
            diagnosis.append("✓ Usage metadata present\n");
            
            if (usage.has("promptTokenCount")) {
                diagnosis.append("  - Prompt tokens: ").append(usage.get("promptTokenCount").asInt()).append("\n");
            }
            if (usage.has("totalTokenCount")) {
                diagnosis.append("  - Total tokens: ").append(usage.get("totalTokenCount").asInt()).append("\n");
            }
        }
    }
    
    /**
     * Create fallback chat response when AI is not available
     */
    private String createFallbackChatResponse(MaskingIntent intent, List<FieldSuggestion> suggestions) {
        StringBuilder response = new StringBuilder();
        
        response.append("I understand you want to create a data masking policy. ");
        response.append("However, I'm currently not available in your region for AI processing. ");
        response.append("\n\n");
        
        if (intent != null && intent.getOriginalRequest() != null) {
            response.append("Based on your request: \"").append(intent.getOriginalRequest()).append("\"\n");
            response.append("I suggest using manual policy creation with these settings:\n");
            response.append("- Strategy: ").append(intent.getMaskingStrategy()).append("\n");
            response.append("- Target Role: ").append(intent.getUserRole()).append("\n");
        }
        
        if (suggestions != null && !suggestions.isEmpty()) {
            response.append("\nI've identified these potentially sensitive fields:\n");
            for (FieldSuggestion suggestion : suggestions) {
                response.append("- ").append(suggestion.getFieldName())
                       .append(" (").append(suggestion.getSuggestedStrategy()).append(" masking)\n");
            }
        }
        
        response.append("\nPlease use the manual masking policy creation interface to configure these settings.");
        
        return response.toString();
    }
} 