package com.syncflow.plugin.descriptor;

import java.util.List;

public record PluginDescriptor(
        String pluginId,
        String pluginName,
        String vendor,
        String version,
        String description,
        String connectorType,
        List<String> supportedDatabases,
        String minimumPlatformVersion,
        String maximumPlatformVersion,
        List<String> requiredPermissions,
        String license,
        String icon,
        String documentationUrl) {
}
