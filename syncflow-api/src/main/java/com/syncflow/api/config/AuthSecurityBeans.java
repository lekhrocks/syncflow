package com.syncflow.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Auth beans: BCrypt password encoder, the AuthenticationManager backed by
 * DaoAuthenticationProvider over the user-details service, and the JWT
 * authentication converter that maps the JWT {@code scope} claim to
 * {@code SCOPE_*} authorities and carries the caller's tenant claims (read by
 * TenantFilter / RBAC — tenant is taken from the principal, not client
 * headers).
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
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        // Maps JWT 'scope' -> SCOPE_ authorities and attaches the tenant scope
        // (tid/oid/wid/pid claims) to the token details.
        return new TenantJwtAuthenticationConverter();
    }
}
