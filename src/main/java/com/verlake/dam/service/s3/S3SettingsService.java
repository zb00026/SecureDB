package com.verlake.dam.service.s3;

import com.verlake.dam.entity.S3BucketSettings;
import com.verlake.dam.repository.S3BucketSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class S3SettingsService {
    @Autowired
    private S3BucketSettingsRepository s3BucketSettingsRepository;

    @Transactional
    public S3BucketSettings updateS3BucketSettings(String newBucketName) {
        Optional<S3BucketSettings> existingSettings = s3BucketSettingsRepository.findLatestSettings();
        
        S3BucketSettings settings = existingSettings.orElse(new S3BucketSettings());
        
        // Update previous bucket names
        if (settings.getBucketName() != null) {
            List<String> previousBuckets = new ArrayList<>();
            if (settings.getPreviousBucketNames() != null) {
                previousBuckets.addAll(Arrays.asList(settings.getPreviousBucketNames().split(",")));
            }
            previousBuckets.add(settings.getBucketName());
            settings.setPreviousBucketNames(previousBuckets.stream().collect(Collectors.joining(",")));
        }
        
        settings.setBucketName(newBucketName);
        settings.setUpdatedAt(LocalDateTime.now());
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(LocalDateTime.now());
        }

        return s3BucketSettingsRepository.save(settings);
    }

    public Optional<S3BucketSettings> getCurrentSettings() {
        return s3BucketSettingsRepository.findLatestSettings();
    }
}
