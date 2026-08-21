package com.syncflow.persistence.workflow.repository;

import com.syncflow.persistence.workflow.entity.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, String> {

    List<WorkflowInstanceEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
