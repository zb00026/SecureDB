package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.dto.ColumnSchemaDTO;
import com.verlake.dam.entity.assets.dto.DatabaseSchemaDTO;
import com.verlake.dam.entity.assets.dto.NaturalLanguageQueryDTO;
import com.verlake.dam.entity.assets.dto.TableSchemaDTO;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.exception.AIGeminiException;
import com.verlake.dam.exception.AIPromptException;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.service.ai.GeminiAIService;
import com.verlake.dam.service.assets.common.AssetValidationUtils;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to convert natural language queries to SQL using LLM
 */
@Service
@Slf4j
public class NaturalLanguageToSqlService {
    
    private final DatabaseSchemaService databaseSchemaService;
    private final GeminiAIService geminiAIService;
    private final AssetValidationUtils assetValidationUtils;
    
    public NaturalLanguageToSqlService(DatabaseSchemaService databaseSchemaService,
                                      GeminiAIService geminiAIService,
                                      AssetValidationUtils assetValidationUtils) {
        this.databaseSchemaService = databaseSchemaService;
        this.geminiAIService = geminiAIService;
        this.assetValidationUtils = assetValidationUtils;
    }
    
    /**
     * Convert natural language query to SQL for Accessor
     */
    public Map<String, Object> convertNaturalLanguageToSqlForAccessor(NaturalLanguageQueryDTO request) throws CommonUtils.CryptoException {
        log.info("Converting natural language to SQL for accessor - requestId: {}, query: {}", 
                request.getRequestId(), request.getNaturalLanguageQuery());
        
        if (request.getRequestId() == null) {
            throw new IllegalArgumentException("requestId is required for accessor queries");
        }
        
        // Get database schema for the accessor's access request
        DatabaseSchemaDTO schema = databaseSchemaService.getSchemaForAccessor(request.getRequestId());
        
        // Get asset information
        com.verlake.dam.entity.assets.AccessRequest accessRequest = assetValidationUtils.validateAccessRequest(request.getRequestId());
        Asset asset = accessRequest.getAsset();
        
        return convertNaturalLanguageToSql(request.getNaturalLanguageQuery(), schema, asset);
    }
    
    /**
     * Convert natural language query to SQL for Asset Owner
     */
    public Map<String, Object> convertNaturalLanguageToSqlForAssetOwner(NaturalLanguageQueryDTO request) throws CommonUtils.CryptoException {
        log.info("Converting natural language to SQL for asset owner - assetId: {}, query: {}", 
                request.getAssetId(), request.getNaturalLanguageQuery());
        
        if (request.getAssetId() == null) {
            throw new IllegalArgumentException("assetId is required for asset owner queries");
        }
        
        // Get database schema for the asset owner
        DatabaseSchemaDTO schema = databaseSchemaService.getSchemaForAssetOwner(request.getAssetId());
        
        // Get asset information
        Asset asset = assetValidationUtils.validateAssetOwnership(request.getAssetId());
        
        return convertNaturalLanguageToSql(request.getNaturalLanguageQuery(), schema, asset);
    }
    
    /**
     * Core method to convert natural language to SQL using LLM
     */
    private Map<String, Object> convertNaturalLanguageToSql(String naturalLanguageQuery, 
                                                           DatabaseSchemaDTO schema, 
                                                           Asset asset) {
        try {
            // Format schema as text
            String schemaText = formatSchemaAsText(schema, asset.getDatabaseType());
            
            // Create prompt for LLM
            String prompt = createPrompt(naturalLanguageQuery, schemaText, asset.getDatabaseType());
            
            log.debug("Sending prompt to Gemini AI for NL to SQL conversion");
            
            // Call Gemini AI
            String aiResponse = geminiAIService.generateResponse(prompt);
            
            // Check if the response is an error message
            if (isErrorResponse(aiResponse)) {
                return createErrorResponse(naturalLanguageQuery, aiResponse, schema, asset);
            }
            
            // Extract SQL from response (remove markdown code blocks if present)
            String sqlQuery = extractSqlFromResponse(aiResponse);
            
            log.info("Successfully converted NL query to SQL: {}", sqlQuery);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sqlQuery", sqlQuery);
            response.put("naturalLanguageQuery", naturalLanguageQuery);
            response.put("databaseName", schema.getDatabaseName());
            response.put("databaseType", asset.getDatabaseType().name());
            response.put("success", true);
            
            return response;
            
        } catch (AIPromptException e) {
            log.error("AI prompt error converting natural language to SQL: {}", e.getMessage(), e);
            return createErrorResponse(naturalLanguageQuery, 
                    "AI service encountered an error processing the request: " + e.getMessage(), 
                    schema, asset);
        } catch (AIGeminiException e) {
            log.error("Gemini AI error converting natural language to SQL: {}", e.getMessage(), e);
            return createErrorResponse(naturalLanguageQuery, 
                    "Gemini AI service error: " + e.getMessage(), 
                    schema, asset);
        } catch (DatabaseAccessException e) {
            log.error("Database access error converting natural language to SQL: {}", e.getMessage(), e);
            return createErrorResponse(naturalLanguageQuery, 
                    "Failed to process the query: " + e.getMessage(), 
                    schema, asset);
        } catch (Exception e) {
            log.error("Unexpected error converting natural language to SQL: {}", e.getMessage(), e);
            return createErrorResponse(naturalLanguageQuery, 
                    "An unexpected error occurred: " + e.getMessage(), 
                    schema, asset);
        }
    }
    
    /**
     * Check if the AI response is an error message
     */
    private boolean isErrorResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return true;
        }
        
        String lowerResponse = response.toLowerCase();
        
        // Check for location restriction errors (both data masking and NL to SQL messages)
        if (response.contains(Constants.getMessage(Constants.GEMINI_LOCATION_RESTRICTION_RESPONSE_KEY)) ||
            response.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_LOCATION_RESTRICTION_KEY)) ||
            lowerResponse.contains(Constants.GEMINI_ERROR_NOT_AVAILABLE_IN_REGION) ||
            (lowerResponse.contains("location") && lowerResponse.contains("not supported")) ||
            (lowerResponse.contains("region") && lowerResponse.contains("not available"))) {
            return true;
        }
        
        // Check for circuit breaker errors (both data masking and NL to SQL messages)
        if (response.contains(Constants.getMessage(Constants.GEMINI_CIRCUIT_BREAKER_FALLBACK_KEY)) ||
            response.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_CIRCUIT_BREAKER_KEY)) ||
            lowerResponse.contains("circuit breaker")) {
            return true;
        }
        
        // Check for model overloaded errors (both data masking and NL to SQL messages)
        if (response.contains(Constants.getMessage(Constants.GEMINI_MODEL_OVERLOADED_RESPONSE_KEY)) ||
            response.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_MODEL_OVERLOADED_KEY)) ||
            lowerResponse.contains("model overloaded") ||
            lowerResponse.contains("quota exceeded")) {
            return true;
        }
        
        // Check for generic error messages (both data masking and NL to SQL messages)
        return response.contains(Constants.getMessage(Constants.GEMINI_GENERIC_ERROR_RESPONSE_KEY)) ||
               response.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_GENERIC_ERROR_KEY)) ||
               lowerResponse.contains("encountered an error") ||
               lowerResponse.contains("failed to") ||
               lowerResponse.contains("error occurred");
    }
    
    /**
     * Create error response map with context-appropriate error messages for NL to SQL
     */
    private Map<String, Object> createErrorResponse(String naturalLanguageQuery, 
                                                    String errorMessage, 
                                                    DatabaseSchemaDTO schema, 
                                                    Asset asset) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        // Replace data masking error messages with NL to SQL specific messages
        String nlSqlErrorMessage = convertToNlSqlErrorMessage(errorMessage);
        response.put("error", nlSqlErrorMessage);
        response.put("naturalLanguageQuery", naturalLanguageQuery);
        response.put("sqlQuery", null);
        
        if (schema != null) {
            response.put("databaseName", schema.getDatabaseName());
        }
        if (asset != null) {
            response.put("databaseType", asset.getDatabaseType().name());
        }
        
        return response;
    }
    
    /**
     * Convert data masking error messages to NL to SQL specific error messages
     */
    private String convertToNlSqlErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return Constants.getMessage(Constants.GEMINI_NL_SQL_GENERIC_ERROR_KEY);
        }
        
        String lowerMessage = errorMessage.toLowerCase();
        
        // Check if it's a location restriction error
        if (errorMessage.contains(Constants.getMessage(Constants.GEMINI_LOCATION_RESTRICTION_RESPONSE_KEY)) ||
            lowerMessage.contains(Constants.GEMINI_ERROR_NOT_AVAILABLE_IN_REGION) ||
            (lowerMessage.contains("location") && lowerMessage.contains("not supported")) ||
            (lowerMessage.contains("region") && lowerMessage.contains("not available"))) {
            return Constants.getMessage(Constants.GEMINI_NL_SQL_LOCATION_RESTRICTION_KEY);
        }
        
        // Check if it's a circuit breaker error
        if (errorMessage.contains(Constants.getMessage(Constants.GEMINI_CIRCUIT_BREAKER_FALLBACK_KEY)) ||
            lowerMessage.contains("circuit breaker")) {
            return Constants.getMessage(Constants.GEMINI_NL_SQL_CIRCUIT_BREAKER_KEY);
        }
        
        // Check if it's a model overloaded error
        if (errorMessage.contains(Constants.getMessage(Constants.GEMINI_MODEL_OVERLOADED_RESPONSE_KEY)) ||
            lowerMessage.contains("model overloaded") ||
            lowerMessage.contains("quota exceeded")) {
            return Constants.getMessage(Constants.GEMINI_NL_SQL_MODEL_OVERLOADED_KEY);
        }
        
        // Check if it's already an NL to SQL error message
        if (errorMessage.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_LOCATION_RESTRICTION_KEY)) ||
            errorMessage.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_CIRCUIT_BREAKER_KEY)) ||
            errorMessage.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_GENERIC_ERROR_KEY)) ||
            errorMessage.contains(Constants.getMessage(Constants.GEMINI_NL_SQL_MODEL_OVERLOADED_KEY))) {
            return errorMessage;
        }
        
        // For generic errors or unknown errors, use NL to SQL generic error message
        return Constants.getMessage(Constants.GEMINI_NL_SQL_GENERIC_ERROR_KEY);
    }
    
    /**
     * Format database schema as text for LLM
     */
    private String formatSchemaAsText(DatabaseSchemaDTO schema, DatabaseType databaseType) {
        StringBuilder schemaText = new StringBuilder();
        
        appendSchemaHeader(schemaText, schema, databaseType);
        
        for (TableSchemaDTO table : schema.getTables()) {
            appendTableInfo(schemaText, table);
        }
        
        return schemaText.toString();
    }
    
    /**
     * Append schema header information
     */
    private void appendSchemaHeader(StringBuilder schemaText, DatabaseSchemaDTO schema, DatabaseType databaseType) {
        schemaText.append("Database: ").append(schema.getDatabaseName()).append("\n");
        schemaText.append("Database Type: ").append(databaseType.name()).append("\n\n");
        schemaText.append("Schema Structure:\n");
        schemaText.append("==================\n\n");
    }
    
    /**
     * Append table information including columns
     */
    private void appendTableInfo(StringBuilder schemaText, TableSchemaDTO table) {
        appendTableHeader(schemaText, table);
        appendTableComment(schemaText, table);
        appendTableColumns(schemaText, table);
        schemaText.append("\n");
    }
    
    /**
     * Append table header with table name and schema
     */
    private void appendTableHeader(StringBuilder schemaText, TableSchemaDTO table) {
        schemaText.append("Table: ").append(table.getTableName());
        if (table.getSchema() != null && !table.getSchema().isEmpty()) {
            schemaText.append(" (Schema: ").append(table.getSchema()).append(")");
        }
        schemaText.append("\n");
    }
    
    /**
     * Append table comment if available
     */
    private void appendTableComment(StringBuilder schemaText, TableSchemaDTO table) {
        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            schemaText.append("  Comment: ").append(table.getTableComment()).append("\n");
        }
    }
    
    /**
     * Append all columns for a table
     */
    private void appendTableColumns(StringBuilder schemaText, TableSchemaDTO table) {
        schemaText.append("  Columns:\n");
        for (ColumnSchemaDTO column : table.getColumns()) {
            appendColumnInfo(schemaText, column);
        }
    }
    
    /**
     * Append column information including type, constraints, and comment
     */
    private void appendColumnInfo(StringBuilder schemaText, ColumnSchemaDTO column) {
        schemaText.append("    - ").append(column.getColumnName());
        appendColumnType(schemaText, column);
        appendColumnConstraints(schemaText, column);
        appendColumnComment(schemaText, column);
        schemaText.append("\n");
    }
    
    /**
     * Append column type information
     */
    private void appendColumnType(StringBuilder schemaText, ColumnSchemaDTO column) {
        schemaText.append(" (").append(column.getDataType());
        if (column.getColumnType() != null && !column.getColumnType().equals(column.getDataType())) {
            schemaText.append(", ").append(column.getColumnType());
        }
        schemaText.append(")");
    }
    
    /**
     * Append column constraints (NOT NULL and column key)
     */
    private void appendColumnConstraints(StringBuilder schemaText, ColumnSchemaDTO column) {
        if (!column.isNullable()) {
            schemaText.append(" NOT NULL");
        }
        if (column.getColumnKey() != null && !column.getColumnKey().isEmpty()) {
            schemaText.append(" [").append(column.getColumnKey()).append("]");
        }
    }
    
    /**
     * Append column comment if available
     */
    private void appendColumnComment(StringBuilder schemaText, ColumnSchemaDTO column) {
        if (column.getColumnComment() != null && !column.getColumnComment().isEmpty()) {
            schemaText.append(" - ").append(column.getColumnComment());
        }
    }
    
    /**
     * Create prompt for LLM to convert NL to SQL
     */
    private String createPrompt(String naturalLanguageQuery, String schemaText, DatabaseType databaseType) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a SQL expert. Convert the following natural language query to syntactically correct SQL.\n\n");
        prompt.append("Database Type: ").append(databaseType.name()).append("\n\n");
        prompt.append("Database Schema:\n");
        prompt.append(schemaText).append("\n\n");
        prompt.append("Natural Language Query: ").append(naturalLanguageQuery).append("\n\n");
        prompt.append("Instructions:\n");
        prompt.append("1. Use ONLY the table and column names provided in the schema above.\n");
        prompt.append("2. Generate syntactically correct SQL for ").append(databaseType.name()).append(" database.\n");
        prompt.append("3. Use proper SQL syntax, including appropriate JOINs, WHERE clauses, GROUP BY, HAVING, etc.\n");
        prompt.append("4. Return ONLY the SQL query without any explanation or markdown formatting.\n");
        prompt.append("5. Do not include code blocks (```sql or ```).\n");
        prompt.append("6. If the query asks for a count, use COUNT() function.\n");
        prompt.append("7. If the query asks for filtering, use appropriate WHERE conditions.\n");
        prompt.append("8. If the query asks for aggregations, use appropriate GROUP BY clauses.\n");
        prompt.append("9. Ensure table names and column names match exactly as shown in the schema.\n\n");
        prompt.append("SQL Query:");
        
        return prompt.toString();
    }
    
    /**
     * Extract SQL query from AI response (remove markdown code blocks if present)
     */
    private String extractSqlFromResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            throw new DatabaseAccessException("AI service returned empty response", null);
        }
        
        String response = aiResponse.trim();
        
        // Remove markdown code blocks if present
        if (response.startsWith("```")) {
            // Find the end of the code block
            int startIndex = response.indexOf('\n');
            int endIndex = response.lastIndexOf("```");
            
            if (startIndex > 0 && endIndex > startIndex) {
                response = response.substring(startIndex + 1, endIndex).trim();
            } else if (startIndex > 0) {
                // No closing ``` found, just remove opening
                response = response.substring(startIndex + 1).trim();
            }
        }
        
        // Remove any leading/trailing SQL keywords that might be added by AI
        response = response.replaceFirst("^(?i)SELECT\\s+", "SELECT ");
        response = response.replaceFirst("^(?i)SQL:\\s*", "");
        response = response.replaceFirst("^(?i)Query:\\s*", "");
        
        // Clean up any extra whitespace
        response = response.replaceAll("\\s+", " ").trim();
        
        // Ensure it ends with semicolon if it's a complete statement
        if (!response.endsWith(";") && !response.toLowerCase().contains("limit") &&
            // Only add semicolon if it looks like a complete statement
            (response.toLowerCase().startsWith("select") || 
                response.toLowerCase().startsWith("insert") ||
                response.toLowerCase().startsWith("update") ||
                response.toLowerCase().startsWith("delete"))) {
                response = response + ";";
        }
        
        return response;
    }
}


