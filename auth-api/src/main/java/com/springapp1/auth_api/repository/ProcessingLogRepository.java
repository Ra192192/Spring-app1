package com.springapp1.auth_api.repository;

import com.springapp1.auth_api.entity.ProcessingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessingLogRepository
        extends JpaRepository<ProcessingLog, UUID> {
}