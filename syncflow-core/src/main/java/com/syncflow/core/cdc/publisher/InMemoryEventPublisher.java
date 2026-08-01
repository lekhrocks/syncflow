package com.syncflow.core.cdc.publisher;

import com.syncflow.core.cdc.CDCEvent;
import java.util.ArrayList;
import java.util.List;

public class InMemoryEventPublisher implements EventPublisher {

    private final List<CDCEvent> events = new ArrayList<>();

    @Override
    public void publish(CDCEvent event) {
        events.add(event);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        events.clear();
    }

    public List<CDCEvent> published() {
        return List.copyOf(events);
    }

    @Override
    public long count() {
        return events.size();
    }
}
