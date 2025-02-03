package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.User;
import com.verlake.dam.repository.RoleRepository;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.service.KeycloakService;
import com.verlake.dam.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    @Autowired
    private KeycloakService keycloakService;

    @Value("${auth.provider}")
    private String authProvider;

    public UserController(UserRepository userRepository, RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User ID " + id + " does not exist"));
    }

    @PostMapping
    public ResponseEntity<Object> createUser(@RequestBody User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with the email '" + user.getEmail() + "' already exists.");
        }
        if(authProvider.equals("keycloak")) {
            keycloakService.createUser(user.getEmail(),
                    user.getEmail(),
                    user.getName(),
                    user.getName(),
                    user.getPassword());
        }
        // Save the new user
        User createdUser = userRepository.save(user);


        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setName(userDetails.getName());
                    user.setEmail(userDetails.getEmail());
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
}
