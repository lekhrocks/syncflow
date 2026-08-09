package com.syncflow.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/health/**",
            "/api/auth/**",
            // Fleet agent inbound endpoints: public here because the agent
            // cannot do OAuth; gated fail-closed by AgentTokenFilter
            // (X-Agent-Token) when syncflow.agent.token is configured. Control-
            // plane agent ops (list/get/drain/restart) stay authenticated.
            // mTLS is the upgrade path (see ADR-008/009).
            "/api/agents/register",
            "/api/agents/heartbeat",
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
