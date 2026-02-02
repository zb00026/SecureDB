package com.verlake.dam.service.audit_trail;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.AuditTrailFilter;
import com.verlake.dam.entity.dto.RoleBasedAuditTrailFilter;
import com.verlake.dam.exception.CsvExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting audit trails to CSV format
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditTrailCsvExportService {

    private final RoleBasedAuditTrailService roleBasedAuditTrailService;
    private final AuditTrailService auditTrailService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_HEADER = "ID,Timestamp,User,Action,Instance ID,IP Address,Asset ID,Previous Value,New Value,Action Metadata,Synced";

    /**
     * Export audit trails to CSV format with role-based access control
     * 
     * @param filter The filter containing search criteria
     * @return CSV content as byte array
     */
    public byte[] exportAuditTrailsToCsv(RoleBasedAuditTrailFilter filter) {
        log.info("Exporting audit trails to CSV with filter: {}", filter);
        
        // Validate access before processing
        roleBasedAuditTrailService.validateAuditAccess();
        
        // Get all audit trails (without pagination for export)
        filter.setPage(0);
        filter.setPerPage(Integer.MAX_VALUE);
        Page<AuditTrail> auditTrails = roleBasedAuditTrailService.getAuditTrails(filter);
        
        return generateCsvContent(auditTrails.getContent());
    }

    /**
     * Export audit trails to CSV format for admin users (full access)
     * 
     * @param filter The filter containing search criteria
     * @return CSV content as byte array
     */
    public byte[] exportAllAuditTrailsToCsv(AuditTrailFilter filter) {
        log.info("Exporting all audit trails to CSV with filter: {}", filter);
        
        // Get all audit trails (without pagination for export)
        filter.setPage(0);
        filter.setPerPage(Integer.MAX_VALUE);
        Page<AuditTrail> auditTrails = auditTrailService.findAll(filter);
        
        return generateCsvContent(auditTrails.getContent());
    }

    /**
     * Generate CSV content from audit trail list
     * 
     * @param auditTrails List of audit trails to export
     * @return CSV content as byte array
     */
    private byte[] generateCsvContent(List<AuditTrail> auditTrails) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            
            // Write BOM for proper UTF-8 encoding in Excel
            outputStream.write(0xEF);
            outputStream.write(0xBB);
            outputStream.write(0xBF);
            
            // Write CSV header
            writer.write(CSV_HEADER);
            writer.write("\n");
            
            // Write data rows
            for (AuditTrail auditTrail : auditTrails) {
                writeCsvRow(writer, auditTrail);
            }
            
            writer.flush();
            byte[] csvContent = outputStream.toByteArray();
            
            log.info("Generated CSV export with {} audit trail records, size: {} bytes", 
                    auditTrails.size(), csvContent.length);
            
            return csvContent;
            
        } catch (IOException e) {
            log.error("Failed to generate CSV export: {}", e.getMessage(), e);
            throw new CsvExportException("Failed to generate CSV export", e);
        }
    }

    /**
     * Write a single audit trail record as CSV row
     * 
     * @param writer Writer to write to
     * @param auditTrail Audit trail record to write
     * @throws IOException if writing fails
     */
    private void writeCsvRow(Writer writer, AuditTrail auditTrail) throws IOException {

        // ID

        String row = escapeCsvValue(auditTrail.getId() != null ? auditTrail.getId().toString() : "") +
                "," +

                // Timestamp
                escapeCsvValue(auditTrail.getTimestamp() != null ?
                        auditTrail.getTimestamp().format(DATE_TIME_FORMATTER) : "") +
                "," +

                // User
                escapeCsvValue(auditTrail.getUser()) +
                "," +

                // Action
                escapeCsvValue(auditTrail.getAction()) +
                "," +

                // Instance ID
                escapeCsvValue(auditTrail.getInstanceId()) +
                "," +

                // IP Address
                escapeCsvValue(auditTrail.getIpAddress()) +
                "," +

                // Asset ID
                escapeCsvValue(auditTrail.getAsset() != null ?
                        auditTrail.getAsset().getId().toString() : "") +
                "," +

                // Previous Value
                escapeCsvValue(auditTrail.getPreviousValue()) +
                "," +

                // New Value
                escapeCsvValue(auditTrail.getNewValue()) +
                "," +

                // Action Metadata
                escapeCsvValue(auditTrail.getActionMetadata()) +
                "," +

                // Synced
                (auditTrail.isSynced() ? "Yes" : "No");
        
        writer.write(row);
        writer.write("\n");
    }

    /**
     * Escape CSV value to handle commas, quotes, and newlines
     * 
     * @param value Value to escape
     * @return Escaped CSV value
     */
    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        
        // If value contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
}
