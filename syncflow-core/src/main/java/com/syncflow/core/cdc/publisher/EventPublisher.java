package com.syncflow.core.cdc.publisher;

import com.syncflow.core.cdc.CDCEvent;

public interface EventPublisher extends AutoCloseable {

    void publish(CDCEvent event);

    void flush();

    /**
     * Returns the number of events currently held / processed by this publisher.
     */
    long count();

    @Override
    void close();
}
