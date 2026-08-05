package com.syncflow.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Auth beans: BCrypt password encoder, the AuthenticationManager backed by
 * DaoAuthenticationProvider over the user-details service, and the JWT
 * authentication converter that maps the JWT {@code scope} claim to
 * {@code ROLE_*} authorities (consumed by TenantFilter / RBAC).
 */
@Configuration
public class AuthSecurityBeans {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Map JWT 'scope' claim -> ROLE_ authorities. The login token encodes the
        // user's roles into 'scope'; TenantFilter reads authorities for RBAC.
        return new JwtAuthenticationConverter();
    }
}
