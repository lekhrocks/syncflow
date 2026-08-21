package com.syncflow.core.snapshot;

/**
 * A single PK-range of a table for parallel snapshot (F15). Bounds are
 * inclusive on start, exclusive on end; a null bound is open-ended (whole
 * table / to the end).
 *
 * Bounds carry the driver-native PK value type (e.g. {@link Long} for a
 * bigint PK) so they bind correctly to the column — a String bound against a
 * numeric column fails ("operator does not exist: bigint >= character
 * varying"). The {@code index} is the chunk ordinal within the table, used to
 * key per-chunk resume checkpoints.
 */
public record ChunkRange(int index, Object start, Object end) {

    /** The whole table as a single chunk — used by the non-chunked path. */
    public static ChunkRange whole() {
        return new ChunkRange(0, null, null);
    }

    public boolean isWhole() {
        return start() == null && end() == null;
    }
}
