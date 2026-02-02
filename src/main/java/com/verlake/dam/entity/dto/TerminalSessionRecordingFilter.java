package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.terminal.TerminalSessionRecording;
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
public class TerminalSessionRecordingFilter extends PageRequestDTO implements FilterMetaData<TerminalSessionRecording> {
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    private String sessionId;
    private Boolean isActive;
    private String clientIp;
    private String userAgent;
    private String terminalType;
    private String userId;
    private String assetId;
    private Integer minCommandCount;
    private Integer maxCommandCount;
    private Long minDurationSeconds;
    private Long maxDurationSeconds;

    @Override
    public Specification<TerminalSessionRecording> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            addDateRangePredicates(predicates, root, cb);
            addStringFieldPredicates(predicates, root, cb);
            addBooleanFieldPredicates(predicates, root, cb);
            addEntityFieldPredicates(predicates, root, cb);
            addRangeFieldPredicates(predicates, root, cb);

            log.debug("TerminalSessionRecordingFilter conditions: startDate={}, endDate={}, sessionId={}, isActive={}, clientIp={}, userAgent={}, terminalType={}, userId={}, assetId={}, minCommandCount={}, maxCommandCount={}, minDurationSeconds={}, maxDurationSeconds={}",
                startDate, endDate, sessionId, isActive, clientIp, userAgent, terminalType, userId, assetId, minCommandCount, maxCommandCount, minDurationSeconds, maxDurationSeconds);

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addDateRangePredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalSessionRecording> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get("sessionStart"), 
                LocalDateTime.of(startDate, LocalTime.MIN)
            ));
        }
        
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get("sessionStart"), 
                LocalDateTime.of(endDate, LocalTime.MAX)
            ));
        }
    }

    private void addStringFieldPredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalSessionRecording> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        addLikePredicate(predicates, root, cb, sessionId, "sessionId");
        addLikePredicate(predicates, root, cb, clientIp, "clientIp");
        addLikePredicate(predicates, root, cb, userAgent, "userAgent");
        addLikePredicate(predicates, root, cb, terminalType, "terminalType");
    }

    private void addLikePredicate(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalSessionRecording> root, jakarta.persistence.criteria.CriteriaBuilder cb, String value, String fieldName) {
        if (value != null && !value.trim().isEmpty()) {
            predicates.add(cb.like(
                cb.upper(root.get(fieldName)), 
                "%" + value.trim().toUpperCase() + "%"
            ));
        }
    }

    private void addBooleanFieldPredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalSessionRecording> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (isActive != null) {
            predicates.add(cb.equal(root.get("isActive"), isActive));
        }
    }

    private void addEntityFieldPredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalSessionRecording> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (userId != null && !userId.trim().isEmpty()) {
            predicates.add(cb.equal(
                root.get("user").get("id"), 
                userId.trim()
            ));
        }

        if (assetId != null && !assetId.trim().isEmpty()) {
            predicates.add(cb.equal(
                root.get("asset").get("id"), 
                assetId.trim()
            ));
        }
    }

    private void addRangeFieldPredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalSessionRecording> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (minCommandCount != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("commandCount"), minCommandCount));
        }

        if (maxCommandCount != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("commandCount"), maxCommandCount));
        }

        if (minDurationSeconds != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("durationSeconds"), minDurationSeconds));
        }

        if (maxDurationSeconds != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("durationSeconds"), maxDurationSeconds));
        }
    }
}
