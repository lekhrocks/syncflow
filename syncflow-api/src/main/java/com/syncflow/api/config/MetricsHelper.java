package com.syncflow.api.config;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Thin wrapper around {@link MeterRegistry#counter} to cut boilerplate.
 * Every counter call in the codebase follows the same pattern —
 * {@code meterRegistry.counter("name", tags...).increment()} — this
 * utility compresses it to one line.
 */
public final class MetricsHelper {

    private MetricsHelper() {
    }

    /** Increment a counter by 1. */
    public static void increment(MeterRegistry registry, String name, String... tags) {
        registry.counter(name, tags).increment();
    }

    /** Increment a counter by {@code amount}. */
    public static void increment(MeterRegistry registry, String name, long amount, String... tags) {
        registry.counter(name, tags).increment(amount);
    }
}
