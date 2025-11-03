package com.verlake.dam.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "s3_bucket_settings")
@Data
public class S3BucketSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bucketName;

    @Column(name = "previous_bucket_names", columnDefinition = "TEXT")
    private String previousBucketNames;  // Comma-separated list of previous buckets

    @Column(name = "local_retention_days")
    private Integer localRetentionDays;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
