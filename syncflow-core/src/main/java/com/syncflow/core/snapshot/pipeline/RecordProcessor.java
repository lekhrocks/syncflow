package com.syncflow.core.snapshot.pipeline;

import java.util.Map;

@FunctionalInterface
public interface RecordProcessor {

    Map<String, Object> process(Map<String, Object> record, ProcessingContext ctx);

    default RecordProcessor andThen(RecordProcessor next) {
        return (record, ctx) -> {
            var intermediate = process(record, ctx);
            if (intermediate == null)
                return null;
            return next.process(intermediate, ctx);
        };
    }
}
