package com.syncflow.core;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class SnapshotPlannerBenchmark {

    @Param({"1000", "100000", "10000000"})
    public long totalRows;

    @Param({"100", "1000", "10000"})
    public int batchSize;

    @Benchmark
    public int calculateBatches() {
        if (batchSize <= 0)
            return 0;
        if (totalRows <= 0)
            return totalRows == 0 ? 0 : 1;
        return (int) Math.ceil((double) totalRows / batchSize);
    }

    @Benchmark
    public int calculateOffset() {
        int batchNumber = (int) (totalRows / batchSize);
        return batchNumber * batchSize;
    }

    @Benchmark
    public long estimateRowsSimple() {
        return totalRows;
    }
}
