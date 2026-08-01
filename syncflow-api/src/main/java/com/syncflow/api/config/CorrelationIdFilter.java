package com.syncflow.api.config;

import com.syncflow.common.correlation.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var id = request.getHeader(CORRELATION_ID_HEADER);
        if (id == null || id.isBlank()) {
            id = CorrelationId.generate();
        }
        CorrelationId.set(id);
        try {
            response.setHeader(CORRELATION_ID_HEADER, id);
            chain.doFilter(request, response);
        } finally {
            CorrelationId.remove();
        }
    }
}
