package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.utils.Constants;
import jakarta.persistence.criteria.Predicate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;

@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class AuditTrailFilter extends PageRequestDTO implements FilterMetaData<AuditTrail> {
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    private String action;
    private String user;
    private String ipAddress;
    private String previousValue;
    private String newValue;
    private Long assetId;

    @Override
    public Specification<AuditTrail> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(Constants.TIMESTAMP_NAME), 
                    LocalDateTime.of(startDate, LocalTime.MIN)
                ));
            }
            
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get(Constants.TIMESTAMP_NAME), 
                    LocalDateTime.of(endDate, LocalTime.MAX)
                ));
            }

            if (action != null && !action.trim().isEmpty()) {
                predicates.add(cb.like(
                        cb.upper(root.get("action")),
                        "%" + action.trim().toUpperCase() + "%"
                ));
            }

            if (user != null && !user.trim().isEmpty()) {
                predicates.add(cb.like(
                    cb.upper(root.get("user")), 
                    "%" + user.trim().toUpperCase() + "%"
                ));
            }

            if (previousValue != null && !previousValue.trim().isEmpty()) {
                predicates.add(cb.like(
                        cb.upper(root.get("previousValue")),
                        "%" + previousValue.trim().toUpperCase() + "%"
                ));
            }

            if (newValue != null && !newValue.trim().isEmpty()) {
                predicates.add(cb.like(
                        cb.upper(root.get("newValue")),
                        "%" + newValue.trim().toUpperCase() + "%"
                ));
            }

            if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                predicates.add(cb.like(
                    cb.upper(root.get("ipAddress")), 
                    "%" + ipAddress.trim().toUpperCase() + "%"
                ));
            }

            if (assetId != null) {
                predicates.add(cb.equal(root.get(Constants.AUDIT_TRAIL_FIELD_ASSET).get(Constants.FIELD_ID), assetId));
            }

            log.debug("Filter conditions: startDate={}, endDate={}, action={}, user={}, previousValue={}, newValue={}, ipAddress={}, assetId={}",
                startDate, endDate, action, user, previousValue, newValue, ipAddress, assetId);

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}