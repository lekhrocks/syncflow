package com.syncflow.core;

import com.syncflow.core.pipeline.mapping.*;
import com.syncflow.core.pipeline.transform.TransformationRule;
import com.syncflow.core.snapshot.pipeline.*;
import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class TransformationPipelineBenchmark {

    private RecordProcessor chain;
    private ProcessingContext ctx;
    private Map<String, Object> record;

    @Setup
    public void setup() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm1 = new ColumnMapping("first_name", "firstName", List.of(TransformationRule.rename("firstName")));
        var cm2 = new ColumnMapping("last_name", "lastName",
                List.of(TransformationRule.rename("lastName"), TransformationRule.uppercase()));
        var cm3 = new ColumnMapping("email", "email", List.of(TransformationRule.lowercase()));
        var cm4 = new ColumnMapping("nickname", "nickname", List.of(TransformationRule.defaultValue("N/A")));
        var mapping = new TableMapping("users", "users_dest", null, pk, List.of(cm1, cm2, cm3, cm4), List.of(),
                List.of(), null);
        chain = new FilterProcessor().andThen(new TransformProcessor());
        ctx = new ProcessingContext(null, mapping);

        record = new LinkedHashMap<>();
        record.put("id", 1);
        record.put("first_name", "John");
        record.put("last_name", "DOE");
        record.put("email", "John@Example.COM");
        record.put("nickname", null);
    }

    @Benchmark
    public Map<String, Object> transformSingleRecord() {
        return chain.process(record, ctx);
    }

    @Benchmark
    public int transformBatch() {
        int count = 0;
        for (int i = 0; i < 100; i++) {
            var result = chain.process(record, ctx);
            if (result != null)
                count++;
        }
        return count;
    }
}
