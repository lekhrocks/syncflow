package com.syncflow.persistence.security.audit.repository;

import com.syncflow.persistence.security.audit.entity.AuditRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID> {

    List<AuditRecordEntity> findByTenantIdOrderByEventTimeDesc(String tenantId, Pageable pageable);
}
