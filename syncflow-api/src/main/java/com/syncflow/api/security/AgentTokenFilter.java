package com.syncflow.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * Shared-secret guard for the agent control-plane endpoints.
 *
 * The fleet agent cannot do OAuth flows, and the platform has no mTLS yet, so
 * agent-to-plane calls are PUBLIC in the security chain but fail-closed here:
 * when {@code syncflow.agent.token} is configured (recommended), any
 * agent-INBOUND request (register/heartbeat) must carry a matching
 * {@code X-Agent-Token}; missing/wrong token -> 403. When the token is unset
 * (dev default), agent endpoints are open — a network-level (mTLS / network
 * policy) hardening is the documented upgrade path (see ADR-008/009).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AgentTokenFilter extends OncePerRequestFilter {

    static final String AGENT_TOKEN_HEADER = "X-Agent-Token";

    private final String configuredToken;
    private final AntPathRequestMatcher agentMatcher = new AntPathRequestMatcher("/api/agents/**");
    private static final String INBOUND_REGISTER = "/api/agents/register";
    private static final String INBOUND_HEARTBEAT = "/api/agents/heartbeat";

    public AgentTokenFilter(@Value("${syncflow.agent.token:}") String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only agent-inbound paths are public; the rest pass through unchanged.
        var path = request.getRequestURI();
        return !agentMatcher.matches(request)
                || (!INBOUND_REGISTER.equals(path) && !INBOUND_HEARTBEAT.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (configuredToken == null || configuredToken.isBlank()) {
            // ponytail: token unset = dev default, open. Require it / mTLS in
            // non-dev environments.
            chain.doFilter(request, response);
            return;
        }
        var presented = request.getHeader(AGENT_TOKEN_HEADER);
        if (presented != null && MessageDigest.isEqual(
                configuredToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                presented.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "invalid or missing X-Agent-Token");
    }
}
