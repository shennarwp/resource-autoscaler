package com.resourceautoscaler.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight per-client rate limiter for API and actuator endpoints. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitingFilter extends OncePerRequestFilter {
    private final int limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${app.rate-limit.requests-per-window:120}") int limit,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.limit = Math.max(1, limit);
        this.windowMillis = Duration.ofSeconds(Math.max(1, windowSeconds)).toMillis();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/") || path.startsWith("/actuator/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = request.getRemoteAddr() + ":" + (request.getRequestURI().startsWith("/api/") ? "api" : "actuator");
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt, current.count + 1);
        });

        if (window.count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(Math.max(1, (windowMillis - (now - window.startedAt)) / 1000)));
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
            return;
        }
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(Math.max(0, limit - window.count)));
        filterChain.doFilter(request, response);
    }

    private record Window(long startedAt, int count) {}
}
