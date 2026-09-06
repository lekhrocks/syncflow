package com.syncflow.api.plugin.adapter;

import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.plugin.spi.PluginContext;

/**
 * Unpacks a nested core {@link ConnectorContext} (which holds
 * {@code ConnectionConfiguration}) into the flat
 * {@link PluginContext} the plugin SPI expects.
 */
public final class PluginContextAdapter {

    private PluginContextAdapter() {
    }

    public static PluginContext from(ConnectorContext ctx) {
        var cfg = ctx.config();
        return new PluginContext(
                cfg.host(),
                cfg.port(),
                cfg.database(),
                cfg.username(),
                cfg.password(),
                cfg.properties());
    }
}
