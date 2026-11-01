package com.syncflow.core.runtime;

/** Outbound port for runtime status events. Web transports adapt this port. */
@FunctionalInterface
public interface RuntimeEventSink {

    void emit(String jobId, String eventName, Object payload);
}
