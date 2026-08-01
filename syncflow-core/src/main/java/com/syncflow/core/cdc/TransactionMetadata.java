package com.syncflow.core.cdc;

import java.time.Instant;

public record TransactionMetadata(
        String transactionId,
        Long totalEventCount,
        Instant transactionTimestamp) {
}
