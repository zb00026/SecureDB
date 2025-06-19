package com.verlake.dam.entity.user.dto;

import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.dto.FilterMetaData;
import com.verlake.dam.entity.dto.PageRequestDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.utils.Constants;

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
    
    // Global search field that searches across multiple fields
    private String search;

    @Override
    public Specification<User> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            
            // If global search is provided, use it instead of individual field filters
            if (search != null && !search.trim().isEmpty()) {
                addGlobalSearchPredicate(predicates, root, query, cb);
            } else {
                // Use individual field filters when no global search
                addEmailPredicate(predicates, root, cb);
                addNamePredicates(predicates, root, cb);
                addActivePredicate(predicates, root, cb);
                addRolePredicates(predicates, root, query, cb);
            }

            log.debug("Filter conditions: search={}, email={}, firstName={}, lastName={}, isActive={}, roles={}",
                search, email, firstName, lastName, isActive, roles);

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
            predicates.add(cb.equal(root.get(Constants.USER_FIELD_IS_ACTIVE), isActive));
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

    /**
     * Global search across ID, firstName, lastName, email, active status, and roles
     */
    private void addGlobalSearchPredicate(List<Predicate> predicates, Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String searchTerm = search.trim().toUpperCase();
        List<Predicate> searchPredicates = new ArrayList<>();
        
        // Search by ID (if search term is numeric)
        try {
            Long searchId = Long.parseLong(search.trim());
            searchPredicates.add(cb.equal(root.get("id"), searchId));
        } catch (NumberFormatException e) {
            // Not a number, skip ID search
        }
        
        // Search by first name
        searchPredicates.add(cb.like(cb.upper(root.get("firstName")), "%" + searchTerm + "%"));
        
        // Search by last name  
        searchPredicates.add(cb.like(cb.upper(root.get("lastName")), "%" + searchTerm + "%"));
        
        // Search by email
        searchPredicates.add(cb.like(cb.upper(root.get("email")), "%" + searchTerm + "%"));
        
        // Search by active status (if search term matches status keywords)
        if (searchTerm.contains("ACTIVE") || searchTerm.contains("ENABLED") || searchTerm.contains("TRUE")) {
            searchPredicates.add(cb.equal(root.get(Constants.USER_FIELD_IS_ACTIVE), true));
        } else if (searchTerm.contains("INACTIVE") || searchTerm.contains("DISABLED") || searchTerm.contains("FALSE") || searchTerm.contains("DEACTIVATED")) {
            searchPredicates.add(cb.equal(root.get(Constants.USER_FIELD_IS_ACTIVE), false));
        }
        
        // Search by role names
        query.distinct(true);
        Subquery<Long> roleSubquery = query.subquery(Long.class);
        Root<User> subRoot = roleSubquery.from(User.class);
        Join<User, Role> roleJoin = subRoot.join("roles");
        
        roleSubquery.select(subRoot.get("id"))
            .where(cb.and(
                cb.equal(subRoot.get("id"), root.get("id")),
                cb.like(cb.upper(roleJoin.get("name")), "%" + searchTerm + "%")
            ));
        
        searchPredicates.add(cb.exists(roleSubquery));
        
        // Combine all search predicates with OR
        if (!searchPredicates.isEmpty()) {
            predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
        }
    }
} 