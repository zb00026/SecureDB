package com.verlake.dam.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.converter.exception.JsonConversionException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JsonAttributeConverter implements AttributeConverter<JsonNode, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JsonNode jsonNode) {
        try {
            return (jsonNode == null) ? null : objectMapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            throw new JsonConversionException("Error converting JSON to String", e);
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(String json) {
        try {
            return (json == null || json.isEmpty()) ? null : objectMapper.readTree(json);
        } catch (Exception e) {
            throw new JsonConversionException("Error converting String to JSON", e);
        }
    }
}