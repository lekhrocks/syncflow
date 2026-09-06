package com.syncflow.api.plugin.adapter;

import com.syncflow.core.spi.ConnectorHealth;

import java.time.Instant;
import java.util.Locale;

/**
 * Parses a plugin's health string ("UP" / "DOWN" / "DEGRADED" / "UNKNOWN")
 * into a {@link ConnectorHealth} record.
 */
public final class PluginHealthAdapter {

    private PluginHealthAdapter() {
    }

    public static ConnectorHealth parse(String raw) {
        var now = Instant.now();
        if (raw == null || raw.isBlank()) {
            return new ConnectorHealth(ConnectorHealth.Status.UNKNOWN,
                    "Plugin reported no status", now, 0);
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "UP" -> new ConnectorHealth(ConnectorHealth.Status.UP,
                    "Plugin reports UP", now, 0);
            case "DOWN" -> new ConnectorHealth(ConnectorHealth.Status.DOWN,
                    "Plugin reports DOWN", now, 0);
            case "DEGRADED" -> new ConnectorHealth(ConnectorHealth.Status.DEGRADED,
                    "Plugin reports DEGRADED", now, 0);
            default -> new ConnectorHealth(ConnectorHealth.Status.UNKNOWN,
                    raw, now, 0);
        };
    }
}
