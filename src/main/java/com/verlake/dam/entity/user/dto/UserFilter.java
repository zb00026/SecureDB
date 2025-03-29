package com.verlake.dam.entity.user.dto;

import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.dto.FilterMetaData;
import com.verlake.dam.entity.dto.PageRequestDTO;
import com.verlake.dam.entity.user.User;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Join;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class UserFilter extends PageRequestDTO implements FilterMetaData<User> {
    private String email;
    private String firstName;
    private String lastName;
    private Boolean isActive;
    private List<String> roles;

    @Override
    public Specification<User> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            
            addEmailPredicate(predicates, root, cb);
            addNamePredicates(predicates, root, cb);
            addActivePredicate(predicates, root, cb);
            addRolePredicates(predicates, root, query, cb);

            log.debug("Filter conditions: email={}, firstName={}, lastName={}, isActive={}, roles={}",
                email, firstName, lastName, isActive, roles);

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addEmailPredicate(List<Predicate> predicates, Root<User> root, CriteriaBuilder cb) {
        if (email != null && !email.trim().isEmpty()) {
            predicates.add(cb.like(
                cb.upper(root.get("email")),
                "%" + email.trim().toUpperCase() + "%"
            ));
        }
    }

    private void addNamePredicates(List<Predicate> predicates, Root<User> root, CriteriaBuilder cb) {
        if (firstName != null && !firstName.trim().isEmpty()) {
            predicates.add(cb.like(
                cb.upper(root.get("firstName")),
                "%" + firstName.trim().toUpperCase() + "%"
            ));
        }

        if (lastName != null && !lastName.trim().isEmpty()) {
            predicates.add(cb.like(
                cb.upper(root.get("lastName")),
                "%" + lastName.trim().toUpperCase() + "%"
            ));
        }
    }

    private void addActivePredicate(List<Predicate> predicates, Root<User> root, CriteriaBuilder cb) {
        if (isActive != null) {
            predicates.add(cb.equal(root.get("isActive"), isActive));
        }
    }

    private void addRolePredicates(List<Predicate> predicates, Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (roles != null && !roles.isEmpty()) {
            query.distinct(true);
            
            // Create a single subquery for all roles
            Subquery<Long> roleSubquery = query.subquery(Long.class);
            Root<User> subRoot = roleSubquery.from(User.class);
            Join<User, Role> roleJoin = subRoot.join("roles");
            
            // Create OR conditions for each role
            List<Predicate> rolePredicates = roles.stream()
                .map(roleName -> cb.equal(cb.upper(roleJoin.get("name")), roleName.trim().toUpperCase()))
                .toList();
            
            roleSubquery.select(subRoot.get("id"))
                .where(cb.and(
                    cb.equal(subRoot.get("id"), root.get("id")),
                    cb.or(rolePredicates.toArray(new Predicate[0]))
                ));
            
            predicates.add(cb.exists(roleSubquery));
        }
    }
} 