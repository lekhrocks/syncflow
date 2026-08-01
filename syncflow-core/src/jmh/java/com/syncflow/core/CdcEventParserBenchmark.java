package com.syncflow.core;

import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class CdcEventParserBenchmark {

    private String debeziumJson;

    @Setup
    public void setup() {
        debeziumJson = """
                {
                    "op": "u",
                    "before": {"id": 1001, "name": "OldName", "email": "old@example.com", "status": "active", "created_at": "2024-01-01T00:00:00Z"},
                    "after": {"id": 1001, "name": "NewName", "email": "new@example.com", "status": "inactive", "created_at": "2024-01-01T00:00:00Z"},
                    "source": {"schema": "public", "table": "users", "lsn": "123456789", "ts_ms": 1704067200000}
                }
                """;
    }

    @Benchmark
    public Map<String, Object> parseJson() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(debeziumJson);
        var after = mapper.convertValue(root.get("after"), Map.class);
        return after;
    }

    @Benchmark
    public String extractOperation() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(debeziumJson);
        return root.get("op").asText();
    }
}
