package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.dto.PageRequestDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.entity.user.dto.UserFilter;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.repository.RoleRepository;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.service.UserService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@Slf4j
public class UserController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private KeycloakService keycloakService;

    @Autowired
    private EmailService emailService;

    @Value("${auth.provider}")
    private String authProvider;

    public UserController(UserRepository userRepository, RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Object getAllUsers(UserFilter filter) {
        return userService.getAllUsers(filter);
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User ID " + id + " does not exist");
        }
        return user;
    }

    @PostMapping("/createUserAndSendInvite")
    public ResponseEntity<Object> createUserAndSendInvite(@RequestBody UserDTO userDto) {
        User user = userDto.getUser();
        AuthProvider userAuthProvider = userDto.getAuthProvider();
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with the email '" + user.getEmail() + "' already exists.");
        }
        // Check password complexity
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            userService.checkPasswordComplexity(user.getPassword());
        }
        if (userAuthProvider == AuthProvider.KEYCLOAK) {
            keycloakService.saveUser(user.getEmail(), user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPassword(), false);
        }
        user.setIsActive(false);
        userRepository.save(user);
        String emailTmplFile = "google-invite";
        if (userAuthProvider == AuthProvider.KEYCLOAK) {
            emailTmplFile = "keycloak-invite";
        }
        emailService.sendInvitationEmail(user, emailTmplFile);
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with the email '" + user.getEmail() + "' already exists.");
        }
        
        // Check password complexity
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            userService.checkPasswordComplexity(user.getPassword());
        }
        
        if (authProvider.contains(AuthProvider.KEYCLOAK.toString().toLowerCase())) {
            keycloakService.saveUser(user.getEmail(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPassword(), false);
        }
        // Save the new user
        user.setIsActive(true);
        return userRepository.save(user);
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setFirstName(userDetails.getFirstName());
                    user.setLastName(userDetails.getLastName());
                    user.setEmail(userDetails.getEmail());
                    
                    // Check password complexity if password is being updated
                    if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
                        userService.checkPasswordComplexity(userDetails.getPassword());
                        user.setPassword(userDetails.getPassword());
                    }
                    
                    if (userDetails.getRoles() != null && !userDetails.getRoles().isEmpty()) {
                        Set<Long> roleIds = userDetails.getRoles().stream()
                                .map(Role::getId)
                                .collect(Collectors.toSet());

                        // Fetch roles from the database
                        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));

                        // Ensure all requested roles exist
                        if (roles.size() != roleIds.size()) {
                            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more roles not found");
                        }

                        // Assign new roles to user
                        user.setRoles(roles);
                    }

                    //If Keycloak Auth provider is provided, Needs to update Keycloak user's information when updating user
                    if (authProvider.contains(AuthProvider.KEYCLOAK.toString().toLowerCase())) {
                        keycloakService.saveUser(user.getEmail(),
                                user.getEmail(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getPassword(), false);
                    }
                    return userRepository.save(user);
                })
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User ID " + id + " does not exist"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    userRepository.delete(user);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", Constants.STATUS_SUCCESS);
                    response.put("error_message", "");
                    return ResponseEntity.ok(response);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User ID " + id + " does not exist."));
    }

    @PutMapping("/setApprover/{id}/{approverId}")
    public User setApproverOfUser(@PathVariable Long id, @PathVariable Long approverId) {
        return userService.setApproverForUser(id, approverId);
    }

    @PutMapping("/unsetApprover/{id}")
    public User unsetApproverOfUser(@PathVariable Long id) {
        return userService.unsetApproverForUser(id);
    }

}
