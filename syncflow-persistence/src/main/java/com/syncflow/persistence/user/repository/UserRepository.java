package com.syncflow.persistence.user.repository;

import com.syncflow.persistence.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
