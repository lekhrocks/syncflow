package com.syncflow.api.region;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for multi-region deployment and geo-replication.
 *
 * Example YAML:
 * syncflow:
 * region:
 * local-region: us-east-1
 * primary-region: us-east-1
 * replication-enabled: true
 * auto-failover: true
 * health-check-interval: 30s
 * failover-threshold: 3
 * max-replication-lag-ms: 5000
 * regions:
 * us-east-1:
 * connection-string: postgresql://user:pass@us-east-db:5432/syncflow
 * write-enabled: true
 * eu-west-1:
 * connection-string: postgresql://user:pass@eu-west-db:5432/syncflow
 * write-enabled: false
 */
@Component
@ConfigurationProperties(prefix = "syncflow.region")
@ConditionalOnProperty(name = "syncflow.region.replication-enabled", havingValue = "true", matchIfMissing = false)
public class RegionalProperties {

    private String localRegion = "us-east-1";
    private String primaryRegion = "us-east-1";
    private boolean replicationEnabled = false;
    private boolean autoFailover = false;
    private Duration healthCheckInterval = Duration.ofSeconds(30);
    private int failoverThreshold = 3;
    private long maxReplicationLagMs = 5000;
    private Map<String, RegionConfig> regions = new HashMap<>();

    public static class RegionConfig {

        private String connectionString;
        private boolean writeEnabled;
        private long connectionTimeoutMs = 5000;
        private long statementTimeoutMs = 30000;

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }

        public boolean isWriteEnabled() {
            return writeEnabled;
        }

        public void setWriteEnabled(boolean writeEnabled) {
            this.writeEnabled = writeEnabled;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getStatementTimeoutMs() {
            return statementTimeoutMs;
        }

        public void setStatementTimeoutMs(long statementTimeoutMs) {
            this.statementTimeoutMs = statementTimeoutMs;
        }
    }

    public String getLocalRegion() {
        return localRegion;
    }

    public void setLocalRegion(String localRegion) {
        this.localRegion = localRegion;
    }

    public String getPrimaryRegion() {
        return primaryRegion;
    }

    public void setPrimaryRegion(String primaryRegion) {
        this.primaryRegion = primaryRegion;
    }

    public boolean isReplicationEnabled() {
        return replicationEnabled;
    }

    public void setReplicationEnabled(boolean replicationEnabled) {
        this.replicationEnabled = replicationEnabled;
    }

    public boolean isAutoFailover() {
        return autoFailover;
    }

    public void setAutoFailover(boolean autoFailover) {
        this.autoFailover = autoFailover;
    }

    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(Duration healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public int getFailoverThreshold() {
        return failoverThreshold;
    }

    public void setFailoverThreshold(int failoverThreshold) {
        this.failoverThreshold = failoverThreshold;
    }

    public long getMaxReplicationLagMs() {
        return maxReplicationLagMs;
    }

    public void setMaxReplicationLagMs(long maxReplicationLagMs) {
        this.maxReplicationLagMs = maxReplicationLagMs;
    }

    public Map<String, RegionConfig> getRegions() {
        return regions;
    }

    public void setRegions(Map<String, RegionConfig> regions) {
        this.regions = regions;
    }

    public RegionConfig getRegionConfig(String region) {
        return regions.getOrDefault(region, new RegionConfig());
    }

    public boolean isWritableRegion(String region) {
        var config = getRegionConfig(region);
        return config != null && config.isWriteEnabled();
    }

    public boolean isPrimaryRegion(String region) {
        return primaryRegion.equals(region);
    }
}
