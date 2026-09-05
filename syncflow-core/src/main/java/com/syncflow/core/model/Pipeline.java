package com.syncflow.core.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class Pipeline {

    private String id;

    @NotBlank
    private String name;

    @NotNull
    private PipelineStatus status;

    @NotNull
    @Valid
    private ConnectionConfiguration source;

    @NotNull
    @Valid
    private ConnectionConfiguration destination;

    @Valid
    private TransformationConfiguration mapping;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Preferred region for this pipeline (e.g., "us-east-1", "eu-west-1").
     * Null means no regional preference.
     */
    private String preferredRegion;

    /**
     * Regional deployment strategy for this pipeline.
     */
    private RegionStrategy regionStrategy = RegionStrategy.ACTIVE_ACTIVE;

    /**
     * Strategy for regional pipeline deployment.
     */
    public enum RegionStrategy {
        /** Run this pipeline in all regions (redundancy). */
        ACTIVE_ACTIVE,
        /** Run in primary region only; failover to standby on primary failure. */
        PRIMARY_STANDBY,
        /** Run in single specified region only (compliance, local data). */
        LOCAL_ONLY
    }

    public Pipeline() {
    }

    public Pipeline(
            String name,
            ConnectionConfiguration source,
            ConnectionConfiguration destination,
            TransformationConfiguration mapping) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.status = PipelineStatus.CREATED;
        this.source = source;
        this.destination = destination;
        this.mapping = mapping;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PipelineStatus getStatus() {
        return this.status;
    }

    public void setStatus(PipelineStatus status) {
        this.status = status;
    }

    public ConnectionConfiguration getSource() {
        return this.source;
    }

    public void setSource(ConnectionConfiguration source) {
        this.source = source;
    }

    public ConnectionConfiguration getDestination() {
        return this.destination;
    }

    public void setDestination(ConnectionConfiguration destination) {
        this.destination = destination;
    }

    public TransformationConfiguration getMapping() {
        return this.mapping;
    }

    public void setMapping(TransformationConfiguration mapping) {
        this.mapping = mapping;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPreferredRegion() {
        return this.preferredRegion;
    }

    public void setPreferredRegion(String preferredRegion) {
        this.preferredRegion = preferredRegion;
    }

    public RegionStrategy getRegionStrategy() {
        return this.regionStrategy;
    }

    public void setRegionStrategy(RegionStrategy regionStrategy) {
        this.regionStrategy = regionStrategy;
    }
}
