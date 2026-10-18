package com.finance.support.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimiterTest {
    @Test void limitsAttemptsByPeerAddressAndResetsAfterWindow() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L);
        var limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 10; i++) assertThat(attempt(limiter, "192.0.2.1")).isEqualTo(200);
        assertThat(attempt(limiter, "192.0.2.1")).isEqualTo(429);
        assertThat(attempt(limiter, "192.0.2.2")).isEqualTo(200);
        when(clock.millis()).thenReturn(602_000L);
        assertThat(attempt(limiter, "192.0.2.1")).isEqualTo(200);
    }

    private int attempt(LoginRateLimiter limiter, String address) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr(address);
        var response = new MockHttpServletResponse();
        limiter.doFilter(request, response, (req, res) -> {});
        return response.getStatus();
    }
}
