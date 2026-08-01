package com.syncflow.api.controller;

import com.syncflow.api.plugin.PluginManager;
import com.syncflow.api.plugin.PluginManager.PluginEntry;
import com.syncflow.api.plugin.PluginManager.PluginInstallResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private final PluginManager pluginManager;

    public PluginController(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @GetMapping
    public ResponseEntity<List<PluginEntry>> list() {
        return ResponseEntity.ok(pluginManager.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PluginEntry> get(@PathVariable String id) {
        return pluginManager.get(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/install")
    public ResponseEntity<PluginInstallResult> install(@RequestParam("file") MultipartFile file) {
        try {
            var temp = File.createTempFile("plugin-", ".jar");
            file.transferTo(temp);
            var result = pluginManager.install(temp);
            temp.delete();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new PluginInstallResult(false, "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Map<String, Object>> enable(@PathVariable String id) {
        var ok = pluginManager.enable(id);
        return ResponseEntity.ok(Map.of("pluginId", id, "enabled", ok));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Map<String, Object>> disable(@PathVariable String id) {
        var ok = pluginManager.disable(id);
        return ResponseEntity.ok(Map.of("pluginId", id, "disabled", ok));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> uninstall(@PathVariable String id) {
        var ok = pluginManager.uninstall(id);
        return ResponseEntity.ok(Map.of("pluginId", id, "uninstalled", ok));
    }

    @GetMapping("/{id}/capabilities")
    public ResponseEntity<Map<String, Object>> capabilities(@PathVariable String id) {
        return pluginManager.get(id)
                .map(entry -> {
                    var caps = entry.connector().capabilities();
                    return ResponseEntity.<Map<String, Object>>ok(Map.of(
                            "pluginId", id,
                            "capabilities", Map.of(
                                    "metadata", caps.supportsMetadata(),
                                    "snapshot", caps.supportsSnapshot(),
                                    "cdc", caps.supportsCdc(),
                                    "destination", caps.supportsDestination(),
                                    "streaming", caps.supportsStreaming())));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
