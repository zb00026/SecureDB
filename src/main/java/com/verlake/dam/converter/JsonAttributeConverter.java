package com.verlake.dam.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.converter.exception.JsonConversionException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

@Converter(autoApply = true)
public class JsonAttributeConverter implements AttributeConverter<JsonNode, Object> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object convertToDatabaseColumn(JsonNode jsonNode) {
        try {
            if (jsonNode == null) {
                return null;
            }
            
            // Always return JSON string for all database types
            // Let the database handle the conversion based on column type:
            // - PostgreSQL and MySQL: json column will parse the string
            // - MSSQL: nvarchar(max) column will store the string directly
            return objectMapper.writeValueAsString(jsonNode);
            
        } catch (Exception e) {
            throw new JsonConversionException("Error converting JSON to database format", e);
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(Object dbData) {
        try {
            if (dbData == null) {
                return null;
            }
            
            String json;
            if (dbData instanceof PGobject) {
                // Handle PostgreSQL JSON objects when reading from database
                json = ((PGobject) dbData).getValue();
            } else {
                // Handle string data from all other databases
                json = dbData.toString();
            }
            
            return (json == null || json.isEmpty()) ? null : objectMapper.readTree(json);
        } catch (Exception e) {
            throw new JsonConversionException("Error converting database data to JSON", e);
        }
    }
}