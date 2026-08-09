package com.syncflow.api.workflow.repository;

import com.syncflow.api.workflow.entity.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, String> {

    List<WorkflowInstanceEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
