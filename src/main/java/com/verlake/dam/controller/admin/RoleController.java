package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.Role;
import com.verlake.dam.repository.RoleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private final RoleRepository roleRepository;


    public RoleController(RoleRepository roleRepository) {

        this.roleRepository = roleRepository;
    }

    @GetMapping
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

}
