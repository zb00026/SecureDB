package com.verlake.dam.service.firebase;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.exception.FirebaseMessagingOperationException;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class FirebaseMessagingService {

    private final NotificationTaskRepository notificationTaskRepository;

    public FirebaseMessagingService(NotificationTaskRepository notificationTaskRepository) {
        this.notificationTaskRepository = notificationTaskRepository;
    }

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

    public void setAssetObjectsFailureNotification(User assetOwner, Asset asset) throws JsonParseException {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("Update Asset Objects Failure");
        notificationMessage.setBody(String.format("Hi, %s %s\n Failed to update asset objects of %s",
                assetOwner.getFirstName(), assetOwner.getLastName(), asset.getName()));
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put(Constants.NOTIFY_DATA_ATTR_RECEIVER_ID, assetOwner.getId().toString());
        notificationData.put("messageType", "0"); //1 : success, 0: fail
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic(Constants.DAM_NOTIFICATION_TOPIC);

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(assetOwner);
        task.setSender(null);
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
        task.setEmailType(EmailType.ASSET_OWNER_UPDATE_ASSET_OBJECT_ERROR);
        notificationTaskRepository.save(task);
    }
}
