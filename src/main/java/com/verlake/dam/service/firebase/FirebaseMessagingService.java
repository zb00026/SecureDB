package com.verlake.dam.service.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.exception.FirebaseMessagingOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FirebaseMessagingService {

    public void sendNotification(NotificationMessage notificationMessage) throws FirebaseMessagingOperationException {
        try {
            Message message = Message.builder()
                .setTopic(notificationMessage.getTopic())
                .setNotification(Notification.builder()
                    .setTitle(notificationMessage.getTitle())
                    .setBody(notificationMessage.getBody())
                    .build())
                .putAllData(notificationMessage.getData())
                .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: " + response);
        } catch (Exception e) {
            log.error("Error sending Firebase message", e);
            throw new FirebaseMessagingOperationException("Failed to send Firebase notification", e);
        }
    }
} 