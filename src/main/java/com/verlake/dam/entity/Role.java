package com.verlake.dam.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;

@Entity
@Table(name = "roles")
@Data
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

}
