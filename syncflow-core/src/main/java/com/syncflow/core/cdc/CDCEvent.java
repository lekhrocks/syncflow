package com.syncflow.core.cdc;

public record CDCEvent(
        EventHeader header,
        EventSource source,
        CDCOperation operation,
        EventPayload payload,
        EventMetadata metadata,
        TransactionMetadata transaction,
        OffsetInformation offset) {
}
