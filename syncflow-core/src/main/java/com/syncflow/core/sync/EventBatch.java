package com.syncflow.core.sync;

import com.syncflow.core.cdc.CDCEvent;
import java.util.List;

public record EventBatch(
        List<CDCEvent> events,
        int batchNumber,
        long createdAt) {
}
