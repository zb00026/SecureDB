package com.verlake.dam.entity.firebase;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.assets.Asset;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User receiver;

    @ManyToOne
    private User requestor;

    @ManyToOne
    private Asset asset;

    @Column(columnDefinition = "TEXT")
    private String notificationMessage;
}