package com.syncflow.api.region;

import java.util.HashMap;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for multi-region deployment.
 *
 * <p>
 * Exposed at /actuator/health/regional
 *
 * <p>
 * Returns:
 * - UP: All regions healthy
 * - DEGRADED: Primary healthy, some replicas down
 * - DOWN: Primary unhealthy
 */
@Component("regional")
@ConditionalOnProperty(name = "syncflow.region.replication-enabled", havingValue = "true", matchIfMissing = false)
public class RegionalHealthEndpoint implements HealthIndicator {

    private final RegionalProperties regionalProperties;
    private final RegionalDataSourceFactory dataSourceFactory;
    private final RegionalConsistencyMonitor consistencyMonitor;

    public RegionalHealthEndpoint(
            RegionalProperties regionalProperties,
            RegionalDataSourceFactory dataSourceFactory,
            RegionalConsistencyMonitor consistencyMonitor) {
        this.regionalProperties = regionalProperties;
        this.dataSourceFactory = dataSourceFactory;
        this.consistencyMonitor = consistencyMonitor;
    }

    @Override
    public Health health() {
        if (!regionalProperties.isReplicationEnabled() || regionalProperties.getRegions().isEmpty()) {
            return Health.up().withDetail("multi_region_enabled", false).build();
        }

        var details = new HashMap<String, Object>();
        var primaryRegion = dataSourceFactory.getCurrentPrimaryRegion();
        var allHealthy = true;
        var anyDown = false;

        // Check primary
        var primaryHealthy = dataSourceFactory.isHealthy(primaryRegion);
        details.put("primary_region", primaryRegion);
        details.put("primary_healthy", primaryHealthy);

        if (!primaryHealthy) {
            anyDown = true;
            allHealthy = false;
        }

        // Check replicas
        var replicaStatus = new HashMap<String, Boolean>();
        for (var region : regionalProperties.getRegions().keySet()) {
            if (region.equals(primaryRegion))
                continue;
            var isHealthy = dataSourceFactory.isHealthy(region);
            replicaStatus.put(region, isHealthy);
            if (!isHealthy)
                allHealthy = false;
        }
        details.put("replicas", replicaStatus);

        // Add consistency status
        details.put("consistency", consistencyMonitor.getConsistencyStatus());

        // Determine overall health
        var status = anyDown ? Health.down() : allHealthy ? Health.up() : Health.status("DEGRADED");

        return status.withDetails(details).build();
    }
}
