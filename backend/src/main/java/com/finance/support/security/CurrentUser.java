package com.finance.support.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CurrentUser {
    public FinanceUser principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FinanceUser user)) {
            throw new AuthenticationCredentialsNotFoundException("Требуется вход.");
        }
        return user;
    }

    public UUID ownerId() { return principal().id(); }
}
