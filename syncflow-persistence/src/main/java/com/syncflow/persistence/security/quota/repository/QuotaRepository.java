package com.syncflow.persistence.security.quota.repository;

import com.syncflow.persistence.security.quota.entity.QuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaRepository extends JpaRepository<QuotaEntity, String> {
}
