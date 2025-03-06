package com.verlake.dam.repository;

import com.verlake.dam.entity.S3BucketSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface S3BucketSettingsRepository extends JpaRepository<S3BucketSettings, Long> {

    @Query("SELECT s FROM S3BucketSettings s ORDER BY s.updatedAt DESC LIMIT 1")
    Optional<S3BucketSettings> findLatestSettings();

    Optional<S3BucketSettings> findByBucketName(String bucketName);
}