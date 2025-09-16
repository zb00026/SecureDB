package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.terminal.TerminalCommandAudit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;

@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class TerminalCommandAuditFilter extends PageRequestDTO implements FilterMetaData<TerminalCommandAudit> {
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    private String sessionId;
    private String rawInput;
    private String commandOutput;
    private String riskLevel;
    private Boolean isDangerous;
    private String commandType;
    private String clientIp;
    private String userId;
    private String assetId;

    @Override
    public Specification<TerminalCommandAudit> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            addDateRangePredicates(predicates, root, cb);
            addStringFieldPredicates(predicates, root, cb);
            addBooleanFieldPredicates(predicates, root, cb);
            addEntityFieldPredicates(predicates, root, cb);

            log.debug("TerminalCommandAuditFilter conditions: startDate={}, endDate={}, sessionId={}, rawInput={}, riskLevel={}, isDangerous={}, commandType={}, clientIp={}, userId={}, assetId={}",
                startDate, endDate, sessionId, rawInput, riskLevel, isDangerous, commandType, clientIp, userId, assetId);

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addDateRangePredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalCommandAudit> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get("executedAt"), 
                LocalDateTime.of(startDate, LocalTime.MIN)
            ));
        }
        
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get("executedAt"), 
                LocalDateTime.of(endDate, LocalTime.MAX)
            ));
        }
    }

    private void addStringFieldPredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalCommandAudit> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        addLikePredicate(predicates, root, cb, sessionId, "sessionId");
        addLikePredicate(predicates, root, cb, rawInput, "rawInput");
        addLikePredicate(predicates, root, cb, commandOutput, "commandOutput");
        addLikePredicate(predicates, root, cb, commandType, "commandType");
        addLikePredicate(predicates, root, cb, clientIp, "clientIp");
        
        if (riskLevel != null && !riskLevel.trim().isEmpty()) {
            predicates.add(cb.equal(
                cb.upper(root.get("riskLevel")), 
                riskLevel.trim().toUpperCase()
            ));
        }
    }

    private void addLikePredicate(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalCommandAudit> root, jakarta.persistence.criteria.CriteriaBuilder cb, String value, String fieldName) {
        if (value != null && !value.trim().isEmpty()) {
            predicates.add(cb.like(
                cb.upper(root.get(fieldName)), 
                "%" + value.trim().toUpperCase() + "%"
            ));
        }
    }

    private void addBooleanFieldPredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalCommandAudit> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (isDangerous != null) {
            predicates.add(cb.equal(root.get("isDangerous"), isDangerous));
        }
    }

    private void addEntityFieldPredicates(ArrayList<Predicate> predicates, jakarta.persistence.criteria.Root<TerminalCommandAudit> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (userId != null && !userId.trim().isEmpty()) {
            predicates.add(cb.equal(
                root.get("sessionRecording").get("user").get("id"), 
                userId.trim()
            ));
        }

        if (assetId != null && !assetId.trim().isEmpty()) {
            predicates.add(cb.equal(
                root.get("sessionRecording").get("asset").get("id"), 
                assetId.trim()
            ));
        }
    }
}
