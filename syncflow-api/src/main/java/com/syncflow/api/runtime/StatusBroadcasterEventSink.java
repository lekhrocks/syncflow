package com.syncflow.api.runtime;

import com.syncflow.api.sse.StatusBroadcaster;
import com.syncflow.core.runtime.RuntimeEventSink;
import org.springframework.stereotype.Component;

/** Adapts the HTTP SSE broadcaster to the transport-neutral runtime port. */
@Component
public final class StatusBroadcasterEventSink implements RuntimeEventSink {

    private final StatusBroadcaster broadcaster;

    public StatusBroadcasterEventSink(StatusBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void emit(String jobId, String eventName, Object payload) {
        broadcaster.emit(jobId, eventName, payload);
    }
}
