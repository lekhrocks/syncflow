package com.syncflow.api.region;

import com.syncflow.core.model.Pipeline;
import com.syncflow.core.model.Pipeline.RegionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes pipeline execution decisions based on regional strategy and current
 * region.
 *
 * <p>
 * Handles three deployment modes:
 * - ACTIVE_ACTIVE: Run in all regions (multiple concurrent captures + syncs)
 * - PRIMARY_STANDBY: Run only in primary region (or standby after failover)
 * - LOCAL_ONLY: Run only in specified preferred region
 *
 * <p>
 * Used by CaptureLifecycle, SyncOrchestrator to decide whether to start/stop
 * capture/sync in
 * current region.
 */
@Component
public class RegionalPipelineOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RegionalPipelineOrchestrator.class);

    private final RegionalProperties regionalProperties;
    private final RegionalDataSourceFactory dataSourceFactory;

    public RegionalPipelineOrchestrator(
            RegionalProperties regionalProperties, RegionalDataSourceFactory dataSourceFactory) {
        this.regionalProperties = regionalProperties;
        this.dataSourceFactory = dataSourceFactory;
    }

    /**
     * Determine if a pipeline should be active (capture + sync) in the current
     * region.
     *
     * <p>
     * Returns false if:
     * - Pipeline is PRIMARY_STANDBY and current region is not primary
     * - Pipeline is LOCAL_ONLY and current region != preferredRegion
     *
     * @param pipeline
     *            the pipeline definition
     * @return true if pipeline should run in local region; false otherwise
     */
    public boolean shouldActivatePipelineInRegion(Pipeline pipeline) {
        return shouldActivatePipelineInRegion(pipeline, regionalProperties.getLocalRegion());
    }

    /**
     * Determine if a pipeline should be active in the specified region.
     *
     * @param pipeline
     *            pipeline definition
     * @param region
     *            region to check (e.g., "eu-west-1")
     * @return true if pipeline should run in that region
     */
    public boolean shouldActivatePipelineInRegion(Pipeline pipeline, String region) {
        if (pipeline.getRegionStrategy() == null) {
            pipeline.setRegionStrategy(RegionStrategy.ACTIVE_ACTIVE);
        }

        switch (pipeline.getRegionStrategy()) {
            case ACTIVE_ACTIVE :
                // Run everywhere
                return true;

            case PRIMARY_STANDBY :
                // Run only in primary region (current primary per failover manager)
                var isPrimary = region.equals(dataSourceFactory.getCurrentPrimaryRegion());
                if (!isPrimary) {
                    logger.debug(
                            "Pipeline {} is PRIMARY_STANDBY; skipping in standby region {}",
                            pipeline.getId(),
                            region);
                }
                return isPrimary;

            case LOCAL_ONLY :
                // Run only in preferred region
                if (pipeline.getPreferredRegion() == null) {
                    logger.warn(
                            "Pipeline {} is LOCAL_ONLY but has no preferredRegion; skipping",
                            pipeline.getId());
                    return false;
                }
                var isLocal = region.equals(pipeline.getPreferredRegion());
                if (!isLocal) {
                    logger.debug(
                            "Pipeline {} is LOCAL_ONLY in {}; skipping in region {}",
                            pipeline.getId(),
                            pipeline.getPreferredRegion(),
                            region);
                }
                return isLocal;

            default :
                logger.warn("Unknown region strategy: {}", pipeline.getRegionStrategy());
                return false;
        }
    }

    /**
     * Check if region is currently primary (for routing decisions).
     *
     * @param region
     *            region to check
     * @return true if region is the current primary
     */
    public boolean isPrimaryRegion(String region) {
        return region.equals(dataSourceFactory.getCurrentPrimaryRegion());
    }

    /**
     * Get the current primary region.
     *
     * @return primary region name
     */
    public String getPrimaryRegion() {
        return dataSourceFactory.getCurrentPrimaryRegion();
    }

    /**
     * Get local region.
     *
     * @return local region name
     */
    public String getLocalRegion() {
        return regionalProperties.getLocalRegion();
    }

    /**
     * Check if regional deployment is enabled.
     *
     * @return true if multi-region support is active
     */
    public boolean isMultiRegionEnabled() {
        return regionalProperties.isReplicationEnabled()
                && !regionalProperties.getRegions().isEmpty();
    }

    /**
     * Get all configured regions.
     *
     * @return list of region names
     */
    public java.util.List<String> getAllConfiguredRegions() {
        return new java.util.ArrayList<>(regionalProperties.getRegions().keySet());
    }
}
