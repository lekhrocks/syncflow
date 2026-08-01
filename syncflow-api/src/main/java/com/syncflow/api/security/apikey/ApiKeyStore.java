package com.syncflow.api.security.apikey;

import com.syncflow.tenant.TenantId;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ApiKeyStore {

    private final Map<String, ApiKey> store = new ConcurrentHashMap<>();

    public ApiKey issue(TenantId tenantId, String label, String scope, Instant expiresAt) {
        var raw = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        var hash = hash(raw);
        var prefix = raw.substring(0, 6);
        var key = new ApiKey(UUID.randomUUID(), tenantId, hash,
                prefix, label, scope, Instant.now(), expiresAt, null);
        store.put(hash, key);
        return key;
    }

    public ApiKey validate(String rawKey) {
        var hash = hash(rawKey);
        var k = store.get(hash);
        if (k != null && k.isActive())
            return k;
        return null;
    }

    public boolean revoke(UUID id) {
        for (var entry : store.entrySet()) {
            if (entry.getValue().id().equals(id)) {
                var k = entry.getValue();
                store.put(entry.getKey(),
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
}
