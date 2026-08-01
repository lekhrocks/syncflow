package com.syncflow.api.ops.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetricsRegistry {

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public MetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Counter counter(String name, String... tags) {
        return counters.computeIfAbsent(name + String.join("", tags),
                k -> Counter.builder(name).tags(tags).register(meterRegistry));
    }

    public Timer timer(String name, String... tags) {
        return timers.computeIfAbsent(name + String.join("", tags),
                k -> Timer.builder(name).tags(tags).register(meterRegistry));
    }

    public <T> Gauge gauge(String name, T obj, java.util.function.ToDoubleFunction<T> f, String... tags) {
        return Gauge.builder(name, obj, f).tags(tags).register(meterRegistry);
    }
}
