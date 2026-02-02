package com.verlake.dam.repository;

import com.verlake.dam.entity.Email;
import com.verlake.dam.enums.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {

    List<Email> findByEmailToAndEmailType(String emailTo, EmailType emailType);
}
