package com.syncflow.api.security.apikey;

import com.syncflow.persistence.security.apikey.entity.ApiKeyEntity;
import com.syncflow.persistence.security.apikey.repository.ApiKeyRepository;
import com.syncflow.tenant.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ApiKeyStore {

    private final ApiKeyRepository repository;
    // Read-through cache for the auth hot path; source of truth is the DB.
    private final Map<String, ApiKey> cache = new ConcurrentHashMap<>();

    @Autowired
    public ApiKeyStore(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /** Unit-test seam: in-memory store without a repository. */
    public ApiKeyStore() {
        this.repository = null;
    }

    @Transactional
    public ApiKey issue(TenantId tenantId, String label, String scope, Instant expiresAt) {
        var raw = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        var hash = hash(raw);
        var prefix = raw.substring(0, 6);
        var key = new ApiKey(UUID.randomUUID(), tenantId, hash,
                prefix, label, scope, Instant.now(), expiresAt, null);
        if (repository != null)
            repository.save(toEntity(key));
        cache.put(hash, key);
        return key;
    }

    @Transactional(readOnly = true)
    public ApiKey validate(String rawKey) {
        var hash = hash(rawKey);
        var cached = cache.get(hash);
        if (cached != null)
            return cached.isActive() ? cached : null;
        if (repository == null)
            return null; // unit-test seam
        return repository.findByHashedKey(hash)
                .map(this::toDomain)
                .filter(ApiKey::isActive)
                .map(k -> {
                    cache.put(hash, k);
                    return k;
                })
                .orElse(null);
    }

    @Transactional
    public boolean revoke(UUID id) {
        if (repository != null) {
            var entity = repository.findById(id).orElse(null);
            if (entity == null)
                return false;
            entity.setRevokedAt(Instant.now());
            repository.save(entity);
            cache.remove(entity.getHashedKey());
            return true;
        }
        // Unit-test seam: scan the in-memory cache.
        for (var entry : cache.entrySet()) {
            if (entry.getValue().id().equals(id)) {
                var k = entry.getValue();
                cache.put(entry.getKey(),
                        new ApiKey(k.id(), k.tenantId(), k.hashedKey(), k.prefix(),
                                k.label(), k.scope(), k.createdAt(), k.expiresAt(), Instant.now()));
                return true;
            }
        }
        return false;
    }

    private String hash(String raw) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var bytes = md.digest(raw.getBytes());
            var sb = new StringBuilder();
            for (var b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private ApiKeyEntity toEntity(ApiKey k) {
        var e = new ApiKeyEntity();
        e.setId(k.id());
        e.setTenantId(k.tenantId().value());
        e.setHashedKey(k.hashedKey());
        e.setPrefix(k.prefix());
        e.setLabel(k.label());
        e.setScope(k.scope());
        e.setCreatedAt(k.createdAt());
        e.setExpiresAt(k.expiresAt());
        e.setRevokedAt(k.revokedAt());
        return e;
    }

    private ApiKey toDomain(ApiKeyEntity e) {
        return new ApiKey(e.getId(), TenantId.from(e.getTenantId()), e.getHashedKey(),
                e.getPrefix(), e.getLabel(), e.getScope(), e.getCreatedAt(),
                e.getExpiresAt(), e.getRevokedAt());
    }
}
