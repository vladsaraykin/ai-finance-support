package com.finance.support.security;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CurrentUser current;
    public AuthController(CurrentUser current) { this.current = current; }

    @GetMapping("/me")
    public UserView me() {
        var user = current.principal();
        return new UserView(user.id(), user.getUsername());
    }

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getHeaderName(), token.getToken()); }

    public record UserView(UUID id, String login) {}
    public record CsrfView(String headerName, String token) {}
}
