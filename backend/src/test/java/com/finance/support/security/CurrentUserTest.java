package com.finance.support.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CurrentUserTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void neverFallsBackToConfiguredOwner() {
        assertThatThrownBy(() -> new CurrentUser().ownerId()).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("forged-owner", "", List.of()));
        assertThatThrownBy(() -> new CurrentUser().ownerId()).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test void ownerComesFromAuthenticatedPrincipal() {
        UUID id = UUID.randomUUID();
        var user = new FinanceUser(id, "alice", "hash", true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, "", user.getAuthorities()));
        assertThat(new CurrentUser().ownerId()).isEqualTo(id);
    }
}
