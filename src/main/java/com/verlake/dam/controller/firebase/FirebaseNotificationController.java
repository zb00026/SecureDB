package com.verlake.dam.controller.firebase;

import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.firebase.TopicSubscriptionRequest;
import com.verlake.dam.service.firebase.FCMService;

import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/firebase/notifications")
public class FirebaseNotificationController {
    private final FCMService fcmService;

    public FirebaseNotificationController(FCMService fcmService) {
        this.fcmService = fcmService;
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
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> response = Map.of("result", "Success");
            return ResponseEntity.ok().body(mapper.writeValueAsString(response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Failed to create response\"}");
        }
    }
}