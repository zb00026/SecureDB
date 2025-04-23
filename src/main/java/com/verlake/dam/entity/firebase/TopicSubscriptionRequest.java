package com.verlake.dam.entity.firebase;

import lombok.Data;

@Data
public class TopicSubscriptionRequest {
    private String token;
    private String topic;
}
