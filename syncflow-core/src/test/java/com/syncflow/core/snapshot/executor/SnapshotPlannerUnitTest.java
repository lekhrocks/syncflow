package com.syncflow.core.snapshot.executor;

import com.syncflow.core.snapshot.BatchInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotPlannerUnitTest {

    // --- Batch calculation ---

    @Test
    void batchCalculationExactDivisible() {
        // 1000 rows, batch size 100 → 10 batches
        var totalBatches = calculateBatches(1000, 100);
        assertEquals(10, totalBatches);
    }

    @Test
    void batchCalculationWithRemainder() {
        // 1050 rows, batch size 100 → 11 batches
        var totalBatches = calculateBatches(1050, 100);
        assertEquals(11, totalBatches);
    }

    @Test
    void batchCalculationSingleBatch() {
        var totalBatches = calculateBatches(50, 100);
        assertEquals(1, totalBatches);
    }

    @Test
    void batchCalculationZeroRows() {
        var totalBatches = calculateBatches(0, 100);
        assertEquals(0, totalBatches);
    }

    @Test
    void batchCalculationMinimumBatchSize() {
        assertThrows(IllegalArgumentException.class, () -> calculateBatches(100, 0));
    }

    @Test
    void batchCalculationNegativeBatchSize() {
        assertThrows(IllegalArgumentException.class, () -> calculateBatches(100, -1));
    }

    @Test
    void batchCalculationNegativeRowCount() {
        var batches = calculateBatches(-1, 100);
        assertEquals(1, batches); // treat unknown as 1 batch
    }

    // --- Chunk calculation ---

    @Test
    void chunkCalculation() {
        // 10000 rows, chunk size 5000 → 2 chunks
        var chunks = calculateChunks(10000, 5000);
        assertEquals(2, chunks);
    }

    @Test
    void chunkCalculationRemainder() {
        var chunks = calculateChunks(12000, 5000);
        assertEquals(3, chunks);
    }

    @Test
    void chunkCalculationExactFit() {
        var chunks = calculateChunks(5000, 5000);
        assertEquals(1, chunks);
    }

    // --- Offset calculation ---

    @Test
    void offsetCalculation() {
        var batchSize = 100;
        assertEquals(0, calculateOffset(0, batchSize));
        assertEquals(100, calculateOffset(1, batchSize));
        assertEquals(500, calculateOffset(5, batchSize));
    }

    @Test
    void offsetWithCustomBatchSize() {
        assertEquals(0, calculateOffset(0, 500));
        assertEquals(500, calculateOffset(1, 500));
        assertEquals(2500, calculateOffset(5, 500));
    }

    @Test
    void offsetWithCursorBasedBatch() {
        // Cursor-based pagination: batchNumber * batchSize
        var info = new BatchInformation(3, 1000, "users", null);
        var offset = info.batchNumber() * info.batchSize();
        assertEquals(3000, offset);
    }

    @Test
    void cursorBasedPagination() {
        // When nextCursor is returned, resume from that offset
        var cursor = "5000";
        var info = new BatchInformation(0, 1000, "users", cursor);
        var offset = Integer.parseInt(cursor) + info.batchNumber() * info.batchSize();
        assertEquals(5000, offset);
    }

    @Test
    void batchInfoRoundTrip() {
        var bi = new BatchInformation(2, 500, "orders", "cursor-x");
        assertEquals(2, bi.batchNumber());
        assertEquals(500, bi.batchSize());
        assertEquals("orders", bi.sourceTable());
        assertEquals("cursor-x", bi.cursor());
    }

    // --- Edge cases ---

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000, 10000})
    void varyingBatchSizes(int batchSize) {
        var totalBatches = calculateBatches(100000, batchSize);
        assertTrue(totalBatches > 0);
        // Verify all batches cover the full range
        var covered = totalBatches * batchSize;
        assertTrue(covered >= 100000);
    }

    @Test
    void maximumBatchSizeOffset() {
        // Verify offset doesn't overflow for very large tables
        var info = new BatchInformation(1000000, 10000, "large_table", null);
        var offset = (long) info.batchNumber() * info.batchSize();
        assertEquals(10_000_000_000L, offset);
        assertTrue(offset > 0); // No overflow
    }

    // --- Helper methods that mirror snapshot logic ---

    private int calculateBatches(long totalRows, int batchSize) {
        if (batchSize <= 0)
            throw new IllegalArgumentException("batchSize must be > 0");
        if (totalRows <= 0)
            return totalRows == 0 ? 0 : 1;
        return (int) Math.ceil((double) totalRows / batchSize);
    }

    private int calculateChunks(long totalRows, int chunkSize) {
        if (chunkSize <= 0)
            throw new IllegalArgumentException("chunkSize must be > 0");
        if (totalRows <= 0)
            return 1;
        return (int) Math.ceil((double) totalRows / chunkSize);
    }

    private int calculateOffset(int batchNumber, int batchSize) {
        return batchNumber * batchSize;
    }
}
