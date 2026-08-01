package com.syncflow.api.plugin;

import com.syncflow.plugin.descriptor.PluginDescriptor;
import com.syncflow.plugin.lifecycle.PluginLifecycle;
import com.syncflow.plugin.spi.PluginConnector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

@Component
public class PluginManager {

    private final Map<String, PluginEntry> plugins = new ConcurrentHashMap<>();
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

    public PluginInstallResult install(File jarFile) {
        try (var jar = new JarFile(jarFile)) {
            var manifest = jar.getManifest();
            if (manifest == null) {
                return new PluginInstallResult(false, "No manifest found in JAR");
            }

            var pluginId = manifest.getMainAttributes().getValue("Plugin-Id");
            var pluginName = manifest.getMainAttributes().getValue("Plugin-Name");
            var version = manifest.getMainAttributes().getValue("Plugin-Version");
            var connectorClass = manifest.getMainAttributes().getValue("Plugin-Connector-Class");

            if (pluginId == null || connectorClass == null) {
                return new PluginInstallResult(false, "Manifest missing Plugin-Id or Plugin-Connector-Class");
            }

            var url = jarFile.toURI().toURL();
            var classLoader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
            classLoaders.put(pluginId, classLoader);

            var clazz = classLoader.loadClass(connectorClass);
            var connector = (PluginConnector) clazz.getDeclaredConstructor().newInstance();
            var descriptor = connector.descriptor();

            var fixedDescriptor = new PluginDescriptor(
                    pluginId, pluginName != null ? pluginName : descriptor.pluginName(),
                    descriptor.vendor(), version != null ? version : descriptor.version(),
                    descriptor.description(), descriptor.connectorType(),
                    descriptor.supportedDatabases(), descriptor.minimumPlatformVersion(),
                    descriptor.maximumPlatformVersion(), descriptor.requiredPermissions(),
                    descriptor.license(), descriptor.icon(), descriptor.documentationUrl());

            plugins.put(pluginId, new PluginEntry(fixedDescriptor, connector, PluginLifecycle.INSTALLED));

            return new PluginInstallResult(true, pluginId);
        } catch (Exception e) {
            return new PluginInstallResult(false, "Installation failed: " + e.getMessage());
        }
    }

    public boolean enable(String pluginId) {
        var entry = plugins.get(pluginId);
        if (entry == null)
            return false;
        plugins.put(pluginId, new PluginEntry(entry.descriptor(), entry.connector(), PluginLifecycle.ENABLED));
        return true;
    }

    public boolean disable(String pluginId) {
        var entry = plugins.get(pluginId);
        if (entry == null)
            return false;
        plugins.put(pluginId, new PluginEntry(entry.descriptor(), entry.connector(), PluginLifecycle.DISABLED));
        return true;
    }

    public boolean uninstall(String pluginId) {
        var removed = plugins.remove(pluginId);
        var cl = classLoaders.remove(pluginId);
        if (cl != null) {
            try {
                cl.close();
            } catch (Exception _) {
            }
        }
        return removed != null;
    }

    public Optional<PluginEntry> get(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    public List<PluginEntry> list() {
        return List.copyOf(plugins.values());
    }

    public List<PluginEntry> enabled() {
        return plugins.values().stream()
                .filter(e -> e.lifecycle() == PluginLifecycle.ENABLED)
                .toList();
    }

    public record PluginEntry(
            PluginDescriptor descriptor,
            PluginConnector connector,
            PluginLifecycle lifecycle) {
    }

    public record PluginInstallResult(boolean success, String message) {
    }
}
