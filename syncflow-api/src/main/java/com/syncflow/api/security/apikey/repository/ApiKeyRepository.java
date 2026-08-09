package com.syncflow.api.security.apikey.repository;

import com.syncflow.api.security.apikey.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByHashedKey(String hashedKey);
}
