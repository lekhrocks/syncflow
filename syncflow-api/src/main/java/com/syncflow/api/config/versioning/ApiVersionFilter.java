package com.syncflow.api.config.versioning;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionFilter.class);
    static final String VERSION_HEADER = "Accept-Version";
    static final String DEPRECATION_HEADER = "Sunset";
    static final String DEPRECATION_INFO = "Link";

    private final VersionContext versionContext;

    public ApiVersionFilter(VersionContext versionContext) {
        this.versionContext = versionContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var path = request.getRequestURI();

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        var versionHeader = request.getHeader(VERSION_HEADER);
        ApiVersion version;

        if (versionHeader != null) {
            version = switch (versionHeader.trim()) {
                case "v2", "2", "2026-07-01" -> ApiVersion.V2;
                default -> ApiVersion.V1;
            };
        } else {
            version = ApiVersion.V1;
        }

        versionContext.setVersion(version);

        if (version.isDeprecated()) {
            response.setHeader(DEPRECATION_HEADER, version.deprecationDate());
            response.setHeader(DEPRECATION_INFO,
                    "<https://docs.syncflow.io/api/migration-v1-to-v2>; rel=\"deprecation\"");
        }

        response.setHeader("X-API-Version", version.name());

        try {
            chain.doFilter(request, response);
        } finally {
            versionContext.setVersion(ApiVersion.V1);
        }
    }
}
