package com.syncflow.core.cdc.publisher;

import com.syncflow.core.cdc.CDCEvent;

public interface EventPublisher extends AutoCloseable {

    void publish(CDCEvent event);

    void flush();

    @Override
    void close();
}
