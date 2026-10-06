package com.monitorlatino.ytdetect.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final String expectedApiKey;

    public ApiKeyAuthFilter(@Value("${yt.security.api-key:default-secret-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/api/v1/youtube/websub/callback") || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/v1/youtube")) {
            String apiKeyHeader = request.getHeader("X-API-Key");
            if (apiKeyHeader == null || !apiKeyHeader.equals(expectedApiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized: Invalid or missing X-API-Key\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
