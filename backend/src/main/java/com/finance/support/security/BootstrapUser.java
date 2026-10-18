package com.finance.support.security;

import com.finance.support.application.OwnerProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class BootstrapUser implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Environment environment;
    private final OwnerProperties owner;
    private final ConfigurableApplicationContext context;

    public BootstrapUser(UserRepository users, PasswordEncoder encoder, Environment environment,
                         OwnerProperties owner, ConfigurableApplicationContext context) {
        this.users = users;
        this.encoder = encoder;
        this.environment = environment;
        this.owner = owner;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"none".equals(environment.getProperty("spring.main.web-application-type"))) {
            throw new IllegalArgumentException("Создание пользователя запускается только без HTTP-сервера.");
        }
        String login = environment.getProperty("BOOTSTRAP_LOGIN", "vlad13reg");
        if (!login.matches("[a-z0-9][a-z0-9_.-]{2,63}")) throw new IllegalArgumentException("Некорректный логин.");
        UUID id = UUID.fromString(environment.getProperty("BOOTSTRAP_USER_ID", owner.ownerId().toString()));
        var existing = users.find(login);
        if (existing.isPresent()) {
            if (!existing.get().id().equals(id)) throw new IllegalArgumentException("Логин уже связан с другим владельцем.");
            System.out.println("Пользователь уже существует. Пароль не изменён.");
        } else {
            String password = environment.getProperty("BOOTSTRAP_PASSWORD", "");
            if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("Пароль должен содержать от 12 символов до 72 байт UTF-8.");
            }
            users.create(id, login, encoder.encode(password));
            System.out.println("Пользователь создан. UUID владельца: " + id);
        }
        SpringApplication.exit(context, () -> 0);
    }
}
