package com.syncflow.persistence.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans the persistence module for JPA entities and Spring Data repositories.
 * The root application uses {@code scanBasePackages = "com.syncflow"}, so this
 * configuration is picked up automatically.
 */
@Configuration
@EntityScan(basePackages = "com.syncflow.persistence")
@EnableJpaRepositories(basePackages = "com.syncflow.persistence")
public class PersistenceConfig {
}
