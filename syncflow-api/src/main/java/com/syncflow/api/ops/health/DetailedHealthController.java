package com.syncflow.api.ops.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class DetailedHealthController implements HealthIndicator {

    private final HealthAggregator aggregator;

    public DetailedHealthController(HealthAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public Health health() {
        var details = aggregator.aggregate();
        var status = details.getOrDefault("status", "UNKNOWN");
        if ("UP".equals(status))
            return Health.up().withDetails(details).build();
        if ("DEGRADED".equals(status))
            return Health.status("DEGRADED").withDetails(details).build();
        return Health.down().withDetails(details).build();
    }
}
