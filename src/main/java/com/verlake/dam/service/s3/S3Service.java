package com.verlake.dam.service.s3;

import com.verlake.dam.repository.S3BucketSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.UUID;

@Service
public class S3Service {
    private final S3Client s3Client;
    private final S3BucketSettingsRepository s3BucketSettingsRepository;

    public S3Service(
            @Value("${aws.accessKeyId:#{null}}") String accessKey,
            @Value("${aws.secretKey:#{null}}") String secretKey,
            @Value("${aws.region}") String region,
            S3BucketSettingsRepository s3BucketSettingsRepository) {
        this.s3BucketSettingsRepository = s3BucketSettingsRepository;
        AwsCredentialsProvider credentialsProvider = accessKey != null && secretKey != null
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.create();

        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    public void checkVersioning(String bucketName) throws S3Exception {
        GetBucketVersioningRequest versioningRequest = GetBucketVersioningRequest.builder()
                .bucket(bucketName)
                .build();
        GetBucketVersioningResponse versioning = s3Client.getBucketVersioning(versioningRequest);
        if (versioning.status() != BucketVersioningStatus.ENABLED) {
            throw S3Exception.builder()
                    .message("Versioning is not enabled on the bucket")
                    .build();
        }
    }

    public void checkEncryption(String bucketName) throws S3Exception {
        try {
            GetBucketEncryptionRequest encryptionRequest = GetBucketEncryptionRequest.builder()
                    .bucket(bucketName)
                    .build();
            GetBucketEncryptionResponse encryption = s3Client.getBucketEncryption(encryptionRequest);
            if (encryption.serverSideEncryptionConfiguration().rules().isEmpty()) {
                throw S3Exception.builder()
                        .message("Encryption is not configured on the bucket")
                        .build();
            }
        } catch (S3Exception e) {
            throw S3Exception.builder()
                    .message("Encryption is not enabled on the bucket")
                    .build();
        }
    }

    public void checkPermissions(String bucketName) throws S3Exception {
        String testKey = "test-permissions-" + UUID.randomUUID() + ".txt";
        // Test write
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(testKey)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromString("test content"));
        } catch (S3Exception e) {
            throw S3Exception.builder()
                    .message("Write permission test failed")
                    .build();
        }

        // Test read (should fail)
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(testKey)
                    .build();
            s3Client.getObject(getRequest);
            throw S3Exception.builder()
                    .message("Read permission should not be allowed")
                    .build();
        } catch (S3Exception e) {
            // Expected - read should fail
        }

        // Test delete (should fail)
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(testKey)
                    .build();
            s3Client.deleteObject(deleteRequest);
            throw S3Exception.builder()
                    .message("Delete permission should not be allowed")
                    .build();
        } catch (S3Exception e) {
            // Expected - delete should fail
        }
    }

    public void uploadFile(String fileName, byte[] data) {
        String bucketName = s3BucketSettingsRepository.findLatestSettings()
                .orElseThrow(() -> S3Exception.builder()
                        .message("No active S3 bucket configured")
                        .build())
                .getBucketName();

        String key = "audit-logs/" + fileName;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
    }
}