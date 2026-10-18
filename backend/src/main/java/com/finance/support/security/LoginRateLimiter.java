package com.finance.support.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

// In-memory limiter for the single-instance deployment. Count every login attempt.
final class LoginRateLimiter extends OncePerRequestFilter {
    private static final long WINDOW = 600_000;
    private final Map<String, Window> attempts = new HashMap<>();
    private final Clock clock;

    LoginRateLimiter() { this(Clock.systemUTC()); }
    LoginRateLimiter(Clock clock) { this.clock = clock; }

    private synchronized boolean allow(String address) {
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().started >= WINDOW);
        Window window = attempts.get(address);
        if (window == null) {
            if (attempts.size() >= 4096) return false;
            window = new Window(now);
            attempts.put(address, window);
        }
        return ++window.count <= 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && request.getRequestURI().equals(request.getContextPath() + "/api/auth/login")
                && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "600");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Слишком много попыток входа. Повторите через 10 минут.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        final long started;
        int count;
        Window(long started) { this.started = started; }
    }
}
