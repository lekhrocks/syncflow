package com.syncflow.api.controller;

import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.MetadataResponse;
import com.syncflow.core.metadata.SchemaMetadata;
import com.syncflow.core.metadata.TableMetadata;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connections/{id}")
public class MetadataController {

    private final MetadataDiscoveryService discoveryService;

    public MetadataController(MetadataDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /** GET /api/connections/{id}/metadata — returns schemas */
    @GetMapping("/metadata")
    public ResponseEntity<MetadataResponse<SchemaMetadata>> getSchemas(@PathVariable String id) {
        return ResponseEntity.ok(discoveryService.discoverSchemas(id));
    }

    /** GET /api/connections/{id}/metadata/schemas */
    @GetMapping("/metadata/schemas")
    public ResponseEntity<MetadataResponse<SchemaMetadata>> getSchemasAlt(@PathVariable String id) {
        return ResponseEntity.ok(discoveryService.discoverSchemas(id));
    }

    /** GET /api/connections/{id}/schemas/{schema}/tables (direct path used by tests) */
    @GetMapping("/schemas/{schema}/tables")
    public ResponseEntity<MetadataResponse<TableMetadata>> getTables(
            @PathVariable String id, @PathVariable String schema) {
        return ResponseEntity.ok(discoveryService.discoverTables(id, schema));
    }

    /** GET /api/connections/{id}/metadata/schemas/{schema}/tables */
    @GetMapping("/metadata/schemas/{schema}/tables")
    public ResponseEntity<MetadataResponse<TableMetadata>> getTablesAlt(
            @PathVariable String id, @PathVariable String schema) {
        return ResponseEntity.ok(discoveryService.discoverTables(id, schema));
    }

    @GetMapping({"/schemas/{schema}/tables/{table}", "/metadata/schemas/{schema}/tables/{table}"})
    public ResponseEntity<MetadataResponse<TableMetadata>> getTable(
            @PathVariable String id, @PathVariable String schema,
            @PathVariable String table) {
        var resp = discoveryService.discoverTables(id, schema);
        var filtered = resp.data().stream()
                .filter(t -> t.name().equals(table))
                .toList();
        var result = new MetadataResponse<>(resp.connectionId(), resp.type(),
                filtered, filtered.size(), resp.discoveryTimeMs(), resp.cached(), resp.error());
        return ResponseEntity.ok(result);
    }

    @GetMapping({"/schemas/{schema}/tables/{table}/columns", "/metadata/schemas/{schema}/tables/{table}/columns"})
    public ResponseEntity<MetadataResponse<ColumnMetadata>> getColumns(
            @PathVariable String id, @PathVariable String schema,
            @PathVariable String table) {
        return ResponseEntity.ok(discoveryService.discoverColumns(id, schema, table));
    }

    @GetMapping({"/schemas/{schema}/tables/{table}/indexes", "/metadata/schemas/{schema}/tables/{table}/indexes"})
    public ResponseEntity<MetadataResponse<IndexMetadata>> getIndexes(
            @PathVariable String id, @PathVariable String schema,
            @PathVariable String table) {
        return ResponseEntity.ok(discoveryService.discoverIndexes(id, schema, table));
    }

    @GetMapping({"/schemas/{schema}/tables/{table}/constraints", "/metadata/schemas/{schema}/tables/{table}/constraints"})
    public ResponseEntity<MetadataResponse<ConstraintMetadata>> getConstraints(
            @PathVariable String id, @PathVariable String schema,
            @PathVariable String table) {
        return ResponseEntity.ok(discoveryService.discoverConstraints(id, schema, table));
    }

    @PostMapping({"/metadata/refresh", "/schemas/refresh"})
    public ResponseEntity<Void> refresh(@PathVariable String id) {
        discoveryService.refresh(id);
        return ResponseEntity.ok().build();
    }
}
