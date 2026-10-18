package com.finance.support.application;

import com.finance.support.api.TransactionDraft;
import com.finance.support.domain.*;
import com.finance.support.security.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

// Uses a unique disposable schema. Never points at the production application's schema.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PostgresPersistenceTest {
    @Test void newlyAddedPastExpensesAppearBeforeExistingLaterDatesAfterReload() throws Exception {
        String schema = "finance_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USERNAME"), System.getenv("TEST_DATABASE_PASSWORD"))) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            try {
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
                connection.setSchema(schema);
                var jdbc = JdbcClient.create(dataSource);
                UUID owner = UUID.randomUUID(), foreignOwner = UUID.randomUUID();
                UUID imported = UUID.randomUUID(), yesterday = UUID.randomUUID(), twoDaysAgo = UUID.randomUUID();
                var ids = List.of(imported, yesterday, twoDaysAgo, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
                var dates = List.of("2026-10-18", "2026-10-17", "2026-10-16", "2026-10-15", "2026-10-14", "2026-09-30");
                for (int i = 0; i < ids.size(); i++) {
                    jdbc.sql("""
                            INSERT INTO finance_transactions
                                (id, owner_id, request_key, transaction_type, amount, currency, occurred_at, category, created_at, deleted_at)
                            VALUES (:id, :owner, :key, 'EXPENSE', 1.00, 'RUB', :occurred, 'OTHER', :created, :deleted)
                            """).param("id", ids.get(i)).param("owner", i == 3 ? foreignOwner : owner)
                            .param("key", "history-" + i)
                            .param("occurred", OffsetDateTime.parse(dates.get(i) + "T12:00:00+03:00"))
                            .param("created", OffsetDateTime.parse("2026-10-19T10:00:00Z").plusMinutes(i))
                            .param("deleted", i == 4 ? OffsetDateTime.parse("2026-10-19T11:00:00Z") : null).update();
                }
                var repository = new FinanceTransactionRepository(jdbc);
                var from = OffsetDateTime.parse("2026-10-01T00:00:00+03:00");
                var to = from.plusMonths(1);
                for (int reload = 0; reload < 2; reload++) {
                    var entries = repository.findByPeriod(owner, from, to);
                    assertThat(entries).extracting(FinanceTransaction::id).containsExactly(twoDaysAgo, yesterday, imported);
                    assertThat(entries.getFirst().occurredAt().toInstant())
                            .isEqualTo(OffsetDateTime.parse("2026-10-16T12:00:00+03:00").toInstant());
                }
                assertThat(repository.sumByType(owner, from, to, TransactionType.EXPENSE)).isEqualByComparingTo("3.00");
            } finally {
                try (var statement = connection.createStatement()) { statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
            }
        }
    }

    @Test void migrationsOwnershipCorrectionsDeletionAndReports() throws Exception {
        String schema = "finance_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USERNAME"), System.getenv("TEST_DATABASE_PASSWORD"))) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            try {
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
                connection.setSchema(schema);
                var jdbc = JdbcClient.create(dataSource);
                UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
                var users = new UserRepository(jdbc);
                assertThat(users.create(alice, "alice", "encoded-hash")).isTrue();
                assertThat(users.create(alice, "alice", "changed-hash")).isFalse();
                assertThat(users.find("alice").orElseThrow().getPassword()).isEqualTo("encoded-hash");
                var repository = new FinanceTransactionRepository(jdbc);
                var owner = new OwnerProperties(alice, ZoneId.of("Europe/Moscow"));
                var service = new FinanceService(repository, owner, new CurrentUser());
                var user = new FinanceUser(alice, "alice", "hash", true);
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, "", List.of()));
                var date = OffsetDateTime.parse("2026-10-31T23:30:00+03:00");
                var month = YearMonth.of(2026, 10);
                var draft = new TransactionDraft(TransactionType.EXPENSE, new BigDecimal("1234.56"), "RUB", date,
                        Category.valueOf("Моя категория"), "Store", "Description", "Наличные");
                var saved = service.create(List.of(draft), "test-key").getFirst();
                assertThat(service.create(List.of(draft), "test-key").getFirst().id()).isEqualTo(saved.id());
                assertThat(service.list(month)).hasSize(1);
                assertThat(service.report(month).expenseTotal()).isEqualTo("1234.56");
                assertThat(repository.update(bob, saved.id(), saved)).isEmpty();
                assertThat(repository.softDelete(bob, saved.id())).isFalse();
                var corrected = new TransactionDraft(TransactionType.EXPENSE, new BigDecimal("12.34"), "RUB", date.plusDays(1),
                        draft.category(), "New store", "Corrected description", "Перевод");
                service.update(saved.id(), corrected);
                assertThat(service.list(month)).isEmpty();
                var november = service.list(month.plusMonths(1)).getFirst();
                assertThat(november.description()).isEqualTo("Corrected description");
                assertThat(november.paymentMethod()).isEqualTo("Перевод");
                assertThat(service.report(month.plusMonths(1)).expenseTotal()).isEqualTo("12.34");
                service.delete(saved.id());
                assertThat(service.list(month.plusMonths(1))).isEmpty();
                assertThat(service.report(month.plusMonths(1)).expenseTotal()).isEqualTo("0.00");
                assertThat(repository.update(alice, saved.id(), saved)).isEmpty();
                assertThatThrownBy(() -> service.create(List.of(draft), "test-key"))
                        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                assertThat(jdbc.sql("SELECT count(*) FROM finance_transactions WHERE deleted_at IS NOT NULL").query(Long.class).single()).isEqualTo(1L);
            } finally {
                SecurityContextHolder.clearContext();
                try (var statement = connection.createStatement()) { statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
            }
        }
    }
}
