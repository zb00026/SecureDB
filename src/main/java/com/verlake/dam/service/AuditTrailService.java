package com.verlake.dam.service;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.AuditTrailFilter;
import com.verlake.dam.repository.AuditTrailRepository;
import com.verlake.dam.utils.Constants;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTrailService {
    private final AuditTrailRepository auditTrailRepository;
    private final S3Service s3Service;
    private final HttpServletRequest request;

    @Value("${AUDIT_RETENTION_DAYS:30}")
    private int retentionDays;

    // @Scheduled(cron = "0 */3 * * * *") // Run every 3 minutes
    @Scheduled(cron = "0 0 0 * * ?") // Run at 1 AM every day
    @Transactional
    public void syncAuditTrailToS3() {
        try {
            log.info("Starting synchronizing of AuditTrail To S3");
            // Clean up old records
            cleanupOldRecords();
            List<AuditTrail> unsynced = auditTrailRepository.findBySyncedFalse();
            if (unsynced.isEmpty()) {
                log.info("No data to synchronize Audit Trail");
                return;
            }

            String fileName = "audit_" + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE) + ".parquet";

            // Convert to parquet and upload to S3
            byte[] parquetData = convertToParquet(unsynced);
            s3Service.uploadFile(fileName, parquetData);

            // Mark as synced
            unsynced.forEach(audit -> audit.setSynced(true));
            auditTrailRepository.saveAll(unsynced);

            log.info("Finished synchronizing of AuditTrail To S3");
        } catch (Exception e) {
            log.error("Failed to sync audit trail to S3", e);
            throw new RuntimeException("Failed to sync audit trail to S3", e);
        }
    }

    public void cleanupOldRecords() {
        // Get the latest retention days value
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        auditTrailRepository.deleteSyncedRecordsOlderThan(cutoffDate);
    }

    private byte[] convertToParquet(List<AuditTrail> audits) throws IOException {
        // Create a unique temporary file with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        File tempFile = new File(System.getProperty("java.io.tmpdir"), "audit_" + timestamp + ".parquet");
        
        // Ensure file doesn't exist
        if (tempFile.exists() && !tempFile.delete()) {
            log.warn("Failed to delete existing temporary file: {}", tempFile.getAbsolutePath());
            tempFile.deleteOnExit();
        }

        try {
            // Configure Parquet writer
            try (ParquetWriter<GenericRecord> writer = AvroParquetWriter
                    .<GenericRecord>builder(
                            HadoopOutputFile.fromPath(new Path(tempFile.getAbsolutePath()), new Configuration()))
                    .withSchema(Constants.AUDIT_SCHEMA)
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .withPageSize(4 * 1024 * 1024)
                    .build()) {

                // Convert each audit trail to a GenericRecord and write
                for (AuditTrail audit : audits) {
                    GenericRecord auditRecord = new GenericData.Record(Constants.AUDIT_SCHEMA);
                    auditRecord.put("id", audit.getId());
                    auditRecord.put("timestamp", audit.getTimestamp().toString());
                    auditRecord.put("instanceId", audit.getInstanceId());
                    auditRecord.put("user", audit.getUser());
                    auditRecord.put("action", audit.getAction());
                    auditRecord.put("previousValue", audit.getPreviousValue());
                    auditRecord.put("newValue", audit.getNewValue());
                    auditRecord.put("actionMetadata", audit.getActionMetadata());
                    auditRecord.put("ipAddress", audit.getIpAddress());

                    writer.write(auditRecord);
                }
            }

            return Files.readAllBytes(tempFile.toPath());
        } finally {
            // Always clean up the temp file
            if (tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                tempFile.deleteOnExit(); // Fallback: Request deletion on JVM exit
            }
        }
    }

    public AuditTrail save(AuditTrail audit) {
        return auditTrailRepository.save(audit);
    }

    public Page<AuditTrail> findAll(AuditTrailFilter filter) {
        log.debug("Searching with filter: {}", filter);
        Page<AuditTrail> result = auditTrailRepository.findAll(
            filter.toSpecification(), 
            filter.toPageRequest(Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        log.debug("Found {} results", result.getTotalElements());
        return result;
    }

}