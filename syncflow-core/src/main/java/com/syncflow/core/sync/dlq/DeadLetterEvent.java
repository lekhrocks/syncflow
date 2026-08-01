package com.syncflow.core.sync.dlq;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.sync.FailureReason;
import java.time.Instant;

public record DeadLetterEvent(
        String id,
        String pipelineId,
        CDCEvent originalEvent,
        FailureReason reason,
        int retryCount,
        Instant timestamp) {
}
