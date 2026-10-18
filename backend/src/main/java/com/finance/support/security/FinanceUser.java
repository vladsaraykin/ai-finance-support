package com.finance.support.security;

import org.springframework.security.core.userdetails.User;
import java.util.List;
import java.util.UUID;

public final class FinanceUser extends User {
    private final UUID id;

    public FinanceUser(UUID id, String login, String hash, boolean enabled) {
        super(login, hash, enabled, true, true, true, List.of());
        this.id = id;
    }

    public UUID id() { return id; }
}
