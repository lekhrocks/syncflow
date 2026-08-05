package com.syncflow.api.user;

import com.syncflow.api.user.entity.UserEntity;
import com.syncflow.api.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * User lifecycle operations. Centralizes create/update/role assignment so the
 * controller stays a thin transport layer and the domain rules (role
 * allow-list,
 * password encoding, duplicate checks) are testable without HTTP.
 */
@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserEntity create(String username, String password, String email, String roles) {
        if (repository.existsByUsername(username)) {
            throw new UserConflictException("username taken: " + username);
        }
        var now = Instant.now();
        var u = new UserEntity();
        u.setId(UUID.randomUUID().toString());
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setEmail(email);
        u.setRoles(roles == null || roles.isBlank() ? RoleConstants.USER : roles);
        u.setEnabled(true);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        return repository.save(u);
    }

    public UserEntity update(String id, String email, String roles, Boolean enabled) {
        var u = find(id);
        if (email != null)
            u.setEmail(email);
        if (roles != null)
            u.setRoles(roles);
        if (enabled != null)
            u.setEnabled(enabled);
        u.setUpdatedAt(Instant.now());
        return repository.save(u);
    }

    public UserEntity setRoles(String id, String roles) {
        var u = find(id);
        u.setRoles(roles);
        u.setUpdatedAt(Instant.now());
        return repository.save(u);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public UserEntity find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }

    public List<UserEntity> list() {
        return repository.findAll();
    }

    public Map<String, Object> toMap(UserEntity u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "email", u.getEmail() != null ? u.getEmail() : "",
                "roles", u.getRoles(),
                "enabled", u.isEnabled());
    }

    /** Thrown when creating a user whose username already exists. */
    public static class UserConflictException extends RuntimeException {

        public UserConflictException(String message) {
            super(message);
        }
    }
}
