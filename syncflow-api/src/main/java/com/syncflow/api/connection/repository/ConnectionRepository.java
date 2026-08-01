package com.syncflow.api.connection.repository;

import com.syncflow.api.connection.entity.ConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionRepository extends JpaRepository<ConnectionEntity, String> {
}
