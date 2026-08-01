package com.syncflow.api.metadata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.SchemaMetadata;
import com.syncflow.core.metadata.TableMetadata;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MetadataCache {

    private final Cache<String, List<SchemaMetadata>> schemaCache;
    private final Cache<String, List<TableMetadata>> tableCache;
    private final Cache<String, List<ColumnMetadata>> columnCache;
    private final Cache<String, List<IndexMetadata>> indexCache;
    private final Cache<String, List<ConstraintMetadata>> constraintCache;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public MetadataCache(MetadataProperties props) {
        var ttl = props != null ? props.getTtl() : Duration.ofMinutes(5);
        schemaCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(500).build();
        tableCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(1000).build();
        columnCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(2000).build();
        indexCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(2000).build();
        constraintCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(2000).build();
    }

    public Optional<List<SchemaMetadata>> getSchemas(String key) {
        return get(schemaCache, key);
    }
    public void putSchemas(String key, List<SchemaMetadata> v) {
        schemaCache.put(key, v);
    }

    public Optional<List<TableMetadata>> getTables(String key) {
        return get(tableCache, key);
    }
    public void putTables(String key, List<TableMetadata> v) {
        tableCache.put(key, v);
    }

    public Optional<List<ColumnMetadata>> getColumns(String key) {
        return get(columnCache, key);
    }
    public void putColumns(String key, List<ColumnMetadata> v) {
        columnCache.put(key, v);
    }

    public Optional<List<IndexMetadata>> getIndexes(String key) {
        return get(indexCache, key);
    }
    public void putIndexes(String key, List<IndexMetadata> v) {
        indexCache.put(key, v);
    }

    public Optional<List<ConstraintMetadata>> getConstraints(String key) {
        return get(constraintCache, key);
    }
    public void putConstraints(String key, List<ConstraintMetadata> v) {
        constraintCache.put(key, v);
    }

    public void invalidate(String connectionId) {
        var prefix = connectionId + ":";
        schemaCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        tableCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        columnCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        indexCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        constraintCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    public long hits() {
        return hits.get();
    }
    public long misses() {
        return misses.get();
    }
    public double hitRate() {
        var total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    private <T> Optional<T> get(Cache<String, T> cache, String key) {
        var v = cache.getIfPresent(key);
        if (v != null) {
            hits.incrementAndGet();
            return Optional.of(v);
        }
        misses.incrementAndGet();
        return Optional.empty();
    }
}
