package com.syncflow.api.security.quota.repository;

import com.syncflow.api.security.quota.entity.QuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaRepository extends JpaRepository<QuotaEntity, String> {
}
