package com.verlake.dam.controller.firebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.firebase.TopicSubscriptionRequest;
import com.verlake.dam.service.firebase.FCMService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/firebase/notifications")
public class FirebaseNotificationController {
    private final FCMService fcmService;
    private final ObjectMapper objectMapper;

    public FirebaseNotificationController(FCMService fcmService, ObjectMapper objectMapper) {
        this.fcmService = fcmService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribeToTopic(@RequestBody TopicSubscriptionRequest request) {
        try {
            fcmService.subscribeToTopic(request.getToken(), request.getTopic());
            return createSuccessResponse();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to subscribe: " + e.getMessage());
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribeFromTopic(@RequestBody TopicSubscriptionRequest request) {
        try {
            fcmService.unsubscribeFromTopic(request.getToken(), request.getTopic());
            return createSuccessResponse();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to unsubscribe: " + e.getMessage());
        }
    }

    private ResponseEntity<String> createSuccessResponse() {
        try {
            Map<String, String> response = Map.of("result", "Success");
            return ResponseEntity.ok().body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Failed to create response\"}");
        }
    }
}