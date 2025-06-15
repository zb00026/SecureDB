package com.verlake.dam.repository;

import com.verlake.dam.entity.Email;
import com.verlake.dam.enums.EmailType;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRepository extends JpaRepository<Email, Long> {

    List<Email> findByEmailToAndEmailType(String emailTo, EmailType emailType);
}
