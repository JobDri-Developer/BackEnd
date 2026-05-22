package com.jobdri.jobdri_api.domain.audit.repository;

import com.jobdri.jobdri_api.domain.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
