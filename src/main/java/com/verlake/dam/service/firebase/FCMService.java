package com.verlake.dam.service.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.TopicManagementResponse;
import com.verlake.dam.exception.FirebaseMessagingOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class FCMService {
    public void subscribeToTopic(String token, String topic) throws FirebaseMessagingOperationException {
        try {
            // Subscribe to topic
            TopicManagementResponse response = FirebaseMessaging.getInstance()
                .subscribeToTopic(Collections.singletonList(token), topic);
            
            log.info("Subscribed successfully: " + response.getSuccessCount());
        } catch (FirebaseMessagingException e) {
            log.error("Failed to subscribe to topic: " + e.getMessage());
            throw new FirebaseMessagingOperationException("Failed to subscribe to topic", e);
        }
    }

    public void unsubscribeFromTopic(String token, String topic) throws FirebaseMessagingOperationException {
        try {
            TopicManagementResponse response = FirebaseMessaging.getInstance()
                .unsubscribeFromTopic(Collections.singletonList(token), topic);
            
            log.info("Unsubscribed successfully: " + response.getSuccessCount());
        } catch (FirebaseMessagingException e) {
            log.error("Failed to unsubscribe from topic: " + e.getMessage());
            throw new FirebaseMessagingOperationException("Failed to unsubscribe from topic", e);
        }
    }
}
