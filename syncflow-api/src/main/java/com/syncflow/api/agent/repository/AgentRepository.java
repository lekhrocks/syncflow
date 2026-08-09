package com.syncflow.api.agent.repository;

import com.syncflow.api.agent.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<AgentEntity, String> {

    List<AgentEntity> findByTenantId(String tenantId);
}
