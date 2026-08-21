package com.syncflow.persistence.ops.alert.repository;

import com.syncflow.persistence.ops.alert.entity.AlertEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEventEntity, String> {

    List<AlertEventEntity> findByTenantIdAndAcknowledgedOrderByEventTimeDesc(String tenantId, boolean acknowledged);

    List<AlertEventEntity> findTop500ByTenantIdOrderByEventTimeDesc(String tenantId);

    void deleteByTenantIdAndAcknowledged(String tenantId, boolean acknowledged);
}
