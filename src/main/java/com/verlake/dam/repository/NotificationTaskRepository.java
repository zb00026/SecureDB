package com.verlake.dam.repository;

import com.verlake.dam.entity.firebase.NotificationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {
} 