package com.finance.support.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    private final JdbcClient jdbc;
    public UserRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<FinanceUser> find(String login) {
        return jdbc.sql("SELECT id, login, password_hash, enabled FROM users WHERE login = :login")
                .param("login", login)
                .query((rs, row) -> new FinanceUser(rs.getObject("id", UUID.class), rs.getString("login"),
                        rs.getString("password_hash"), rs.getBoolean("enabled"))).optional();
    }

    public boolean create(UUID id, String login, String hash) {
        return jdbc.sql("""
                INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)
                ON CONFLICT (login) DO NOTHING
                """).param("id", id).param("login", login).param("hash", hash).update() == 1;
    }
}
