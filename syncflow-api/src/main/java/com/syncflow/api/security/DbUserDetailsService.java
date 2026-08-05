package com.syncflow.api.security;

import com.syncflow.api.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Loads a user from the {@code users} table and maps the CSV roles column to
 * {@code ROLE_*} authorities. These flow into
 * {@link com.syncflow.api.security.TenantFilter}
 * via the SecurityContext, feeding the existing RBAC layer.
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    public DbUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var entity = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user: " + username));

        var authorities = Arrays.stream(entity.getRoles().split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        return User.withUsername(entity.getUsername())
                .password(entity.getPasswordHash())
                .authorities(authorities)
                .disabled(!entity.isEnabled())
                .build();
    }
}
