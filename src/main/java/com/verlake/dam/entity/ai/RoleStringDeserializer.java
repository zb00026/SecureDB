package com.verlake.dam.entity.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.verlake.dam.entity.Role;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom deserializer to convert string array to Role objects for AIMaskingPolicy.roles
 */
public class RoleStringDeserializer extends JsonDeserializer<List<Role>> {

    @Override
    public List<Role> deserialize(JsonParser p, DeserializationContext ctxt) 
            throws IOException, JsonProcessingException {
        
        List<Role> roles = new ArrayList<>();
        JsonNode node = p.getCodec().readTree(p);
        
        if (node.isArray()) {
            for (JsonNode roleNode : node) {
                if (roleNode.isTextual()) {
                    // Convert string to Role object
                    Role role = new Role();
                    role.setName(roleNode.asText());
                    roles.add(role);
                } else if (roleNode.isObject() && roleNode.has("name")) {
                    // Handle object with name property
                    Role role = new Role();
                    role.setName(roleNode.get("name").asText());
                    if (roleNode.has("id")) {
                        role.setId(roleNode.get("id").asLong());
                    }
                    roles.add(role);
                }
            }
        }
        
        return roles;
    }
}
