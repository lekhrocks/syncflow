package com.syncflow.api.connection.repository;

import com.syncflow.api.connection.entity.ConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<ConnectionEntity, String> {

    List<ConnectionEntity> findByTenantId(String tenantId);

    Optional<ConnectionEntity> findByIdAndTenantId(String id, String tenantId);
}
