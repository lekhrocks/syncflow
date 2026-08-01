package com.syncflow.common.correlation;

import java.util.UUID;

public final class CorrelationId {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationId() {
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void set(String id) {
        CURRENT.set(id);
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void remove() {
        CURRENT.remove();
    }
}
