package com.syncflow.core.sync;

/**
 * Sync-path processing context. Carries minimal per-batch metadata used by
 * the Filter/Transform processor chain. Distinct from
 * {@link com.syncflow.core.snapshot.pipeline.ProcessingContext}, which
 * carries the full {@code PipelineDesign} + {@code TableMapping}. Both
 * records are intentionally small and live in different packages to keep
 * the snapshot and sync paths decoupled — unifying them would create an
 * awkward union type with most fields unused in one path or the other.
 */
public record ProcessingContext(
        String pipelineId,
        String syncJobId,
        String sourceTable,
        String destinationTable,
        int batchNumber) {
}
