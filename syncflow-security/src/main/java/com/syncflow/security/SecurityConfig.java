package com.syncflow.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/health/**",
            "/api/auth/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/graphiql/**");

    private SecurityConfig() {
    }

    public static List<String> publicPaths() {
        return PUBLIC_PATHS;
    }

    public static boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> {
            var p = pattern.replace("**", ".*").replace("*", "[^/]*");
            return path.matches(p);
        });
    }
}
