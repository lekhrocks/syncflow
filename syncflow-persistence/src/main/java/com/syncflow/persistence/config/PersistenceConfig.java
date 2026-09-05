package com.syncflow.persistence.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans the persistence module for JPA entities and Spring Data repositories.
 * The root application uses {@code scanBasePackages = "com.syncflow"}, so this
 * configuration is picked up automatically.
 *
 * <p>
 * When multi-region replication is enabled
 * (syncflow.region.replication-enabled=true),
 * RegionalRoutingDataSource is auto-wired as the primary datasource for JPA
 * repositories
 * via @Component annotation. It routes writes to primary and reads to local
 * replicas.
 */
@Configuration
@EntityScan(basePackages = "com.syncflow.persistence")
@EnableJpaRepositories(basePackages = "com.syncflow.persistence")
public class PersistenceConfig {
}
