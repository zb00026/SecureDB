package com.verlake.dam.entity.assets;

import com.verlake.dam.entity.user.User;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "access_level_objects")
@Data
public class AccessLevelObject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_name", nullable = false)
    private String objectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_level_id", nullable = false)
    private AccessLevel accessLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User requestor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_request_id", nullable = false)
    private AccessRequest accessRequest;
} 