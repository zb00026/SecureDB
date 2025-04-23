package com.verlake.dam.entity.firebase;

import lombok.Data;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import com.fasterxml.jackson.core.JsonParseException;

@Data
public class NotificationMessage {
    private String title;
    private String body;
    private String topic;
    private Map<String, String> data;

    public static NotificationMessage fromJson(String json) throws JsonParseException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, NotificationMessage.class);
        } catch (Exception e) {
            throw new JsonParseException(null, "Failed to parse NotificationMessage from JSON", e);
        }
    }

    public String toJson() throws JsonParseException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new JsonParseException(null, "Failed to convert NotificationMessage to JSON", e);
        }
    }
} 