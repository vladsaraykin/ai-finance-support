package com.finance.support.security;

import com.finance.support.application.OwnerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BootstrapUserTest {
    private final UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    private final MockEnvironment env = new MockEnvironment().withProperty("spring.main.web-application-type", "none");

    private void run() {
        new BootstrapUser(users, encoder, env, new OwnerProperties(id, ZoneId.of("Europe/Moscow")), context)
                .run(new DefaultApplicationArguments());
    }

    @Test void createsOwnerAccountWithEncodedPassword() {
        env.withProperty("BOOTSTRAP_PASSWORD", "test-password-only");
        when(encoder.encode("test-password-only")).thenReturn("encoded-hash");
        run();
        verify(users).create(id, "vlad13reg", "encoded-hash");
        verify(context).close();
    }

    @Test void rerunKeepsExistingPasswordAndOwner() {
        when(users.find("vlad13reg")).thenReturn(Optional.of(new FinanceUser(id, "vlad13reg", "old-hash", true)));
        run();
        verify(users, never()).create(any(), any(), any());
        verifyNoInteractions(encoder);
    }

    @Test void ownerMismatchStopsWithoutChangingAccount() {
        when(users.find("vlad13reg")).thenReturn(Optional.of(new FinanceUser(UUID.randomUUID(), "vlad13reg", "old-hash", true)));
        assertThatThrownBy(this::run).isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).create(any(), any(), any());
    }

    @Test void weakPasswordAndHttpServerModeAreRejected() {
        env.withProperty("BOOTSTRAP_PASSWORD", "short");
        assertThatThrownBy(this::run).isInstanceOf(IllegalArgumentException.class);
        env.withProperty("BOOTSTRAP_PASSWORD", "test-password-only").withProperty("spring.main.web-application-type", "servlet");
        assertThatThrownBy(this::run).isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).create(any(), any(), any());
    }
}
