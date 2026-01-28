package com.verlake.dam.service.audit_trail;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.AuditTrailFilter;
import com.verlake.dam.repository.AuditTrailRepository;
import com.verlake.dam.service.s3.S3Service;
import com.verlake.dam.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTrailService {
    private final AuditTrailRepository auditTrailRepository;
    private final S3Service s3Service;
    private final com.verlake.dam.repository.S3BucketSettingsRepository s3BucketSettingsRepository;

    @Value("${AUDIT_RETENTION_DAYS:30}")
    private int retentionDays;

    @Scheduled(cron = "0 0 0 */1 * *")
    @Transactional
    public void syncAuditTrailToS3() throws IOException {
        try {
            log.info("Starting synchronizing of AuditTrail To S3");
            int localRetention = resolveLocalRetentionDays();
            LocalDateTime cutoff = LocalDateTime.now().minusDays(localRetention);

            // Export only records beyond local retention that are not yet synced
            List<AuditTrail> unsynced = auditTrailRepository.findBySyncedFalseAndTimestampBefore(cutoff);
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

            // Optionally cleanup old synced records using same retention window
            cleanupOldRecords();

            log.info("Finished synchronizing of AuditTrail To S3");
        } catch (Exception e) {
            log.error("Failed to sync audit trail to S3", e);
            throw new IOException("Failed to sync audit trail to S3", e);
        }
    }

    private int resolveLocalRetentionDays() {
        Optional<com.verlake.dam.entity.S3BucketSettings> settings = s3BucketSettingsRepository.findLatestSettings();
        if (settings.isPresent() && settings.get().getLocalRetentionDays() != null) {
            return settings.get().getLocalRetentionDays();
        }
        return retentionDays;
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
            // Configure Parquet writer without Hadoop (Windows-friendly)
            try (ParquetWriter<GenericRecord> writer = AvroParquetWriter
                    .<GenericRecord>builder(new LocalOutputFile(tempFile.toPath()))
                    .withSchema(Constants.AUDIT_SCHEMA)
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .withPageSize(4 * 1024 * 1024)
                    .build()) {

                // Convert each audit trail to a GenericRecord and write
                for (AuditTrail audit : audits) {
                    GenericRecord auditRecord = new GenericData.Record(Constants.AUDIT_SCHEMA);
                    auditRecord.put("id", audit.getId());
                    auditRecord.put(Constants.TIMESTAMP_NAME, audit.getTimestamp().toString());
                    auditRecord.put("instanceId", audit.getInstanceId());
                    auditRecord.put("user", audit.getUser());
                    auditRecord.put("action", audit.getAction());
                    auditRecord.put("previousValue", audit.getPreviousValue());
                    auditRecord.put("newValue", audit.getNewValue());
                    auditRecord.put("actionMetadata", audit.getActionMetadata());
                    auditRecord.put("ipAddress", audit.getIpAddress());
                    auditRecord.put("assetId", audit.getAsset() != null ? audit.getAsset().getId() : null);

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

    /**
     * Simple local OutputFile implementation to avoid Hadoop dependency on Windows
     */
    private static final class LocalOutputFile implements OutputFile {
        private final java.nio.file.Path path;

        LocalOutputFile(java.nio.file.Path path) {
            this.path = path;
        }

        @Override
        public PositionOutputStream create(long blockSizeHint) throws IOException {
            return new LocalPositionOutputStream(java.nio.file.Files.newOutputStream(path));
        }

        @Override
        public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
            return new LocalPositionOutputStream(java.nio.file.Files.newOutputStream(path));
        }

        @Override
        public boolean supportsBlockSize() {
            return false;
        }

        @Override
        public long defaultBlockSize() {
            return 0;
        }

        @Override
        public java.lang.String getPath() {
            return path.toString();
        }

    }

    private static final class LocalPositionOutputStream extends PositionOutputStream {
        private final java.io.OutputStream delegate;
        private long position = 0;

        LocalPositionOutputStream(java.io.OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getPos() throws IOException {
            return position;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            position++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            position += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    public AuditTrail save(AuditTrail audit) {
        return auditTrailRepository.save(audit);
    }

    public Page<AuditTrail> findAll(AuditTrailFilter filter) {
        log.debug("Searching with filter: {}", filter);
        Page<AuditTrail> result = auditTrailRepository.findAll(
            filter.toSpecification(), 
            filter.toPageRequest(Sort.by(Sort.Direction.DESC, "id"))
        );
        log.debug("Found {} results", result.getTotalElements());
        return result;
    }

    public List<String> getDistinctActions() {
        return auditTrailRepository.findDistinctActions();
    }

}