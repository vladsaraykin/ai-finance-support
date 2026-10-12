package com.finance.support.application;

import com.finance.support.domain.Category;
import com.finance.support.domain.FinanceTransaction;
import com.finance.support.domain.TransactionType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FinanceTransactionRepository {
    private final JdbcClient jdbc;

    public FinanceTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public FinanceTransaction save(UUID ownerId, String requestKey, FinanceTransaction transaction) {
        jdbc.sql("""
                INSERT INTO finance_transactions
                    (id, owner_id, request_key, transaction_type, amount, currency, occurred_at, category, merchant, description)
                VALUES (:id, :ownerId, :requestKey, :type, :amount, :currency, :occurredAt, :category, :merchant, :description)
                ON CONFLICT (owner_id, request_key) DO NOTHING
                """)
                .param("id", transaction.id())
                .param("ownerId", ownerId)
                .param("requestKey", requestKey)
                .param("type", transaction.type().name())
                .param("amount", transaction.amount())
                .param("currency", transaction.currency())
                .param("occurredAt", transaction.occurredAt())
                .param("category", transaction.category().name())
                .param("merchant", transaction.merchant())
                .param("description", transaction.description())
                .update();

        return findByRequestKey(ownerId, requestKey).orElseThrow();
    }

    public Optional<FinanceTransaction> findByRequestKey(UUID ownerId, String requestKey) {
        return jdbc.sql("""
                SELECT id, transaction_type, amount, currency, occurred_at, category, merchant, description
                FROM finance_transactions WHERE owner_id = :ownerId AND request_key = :requestKey
                """)
                .param("ownerId", ownerId)
                .param("requestKey", requestKey)
                .query(this::mapTransaction)
                .optional();
    }

    public List<FinanceTransaction> findByPeriod(UUID ownerId, OffsetDateTime from, OffsetDateTime to) {
        return jdbc.sql("""
                SELECT id, transaction_type, amount, currency, occurred_at, category, merchant, description
                FROM finance_transactions
                WHERE owner_id = :ownerId AND occurred_at >= :from AND occurred_at < :to
                ORDER BY occurred_at DESC, id
                """)
                .param("ownerId", ownerId)
                .param("from", from)
                .param("to", to)
                .query(this::mapTransaction)
                .list();
    }

    public Optional<FinanceTransaction> update(UUID ownerId, UUID id, FinanceTransaction transaction) {
        int count = jdbc.sql("""
                UPDATE finance_transactions
                SET transaction_type = :type, amount = :amount, currency = :currency, occurred_at = :occurredAt,
                    category = :category, merchant = :merchant, description = :description, updated_at = now()
                WHERE owner_id = :ownerId AND id = :id
                """)
                .param("type", transaction.type().name())
                .param("amount", transaction.amount())
                .param("currency", transaction.currency())
                .param("occurredAt", transaction.occurredAt())
                .param("category", transaction.category().name())
                .param("merchant", transaction.merchant())
                .param("description", transaction.description())
                .param("ownerId", ownerId)
                .param("id", id)
                .update();
        return count == 0 ? Optional.empty() : Optional.of(transaction);
    }

    public List<CategoryTotal> categoryTotals(UUID ownerId, OffsetDateTime from, OffsetDateTime to, TransactionType type) {
        return jdbc.sql("""
                SELECT category, currency, SUM(amount) AS total, COUNT(*) AS transaction_count
                FROM finance_transactions
                WHERE owner_id = :ownerId AND occurred_at >= :from AND occurred_at < :to
                  AND transaction_type = :type
                GROUP BY category, currency
                ORDER BY SUM(amount) DESC, category
                """)
                .param("ownerId", ownerId)
                .param("from", from)
                .param("to", to)
                .param("type", type.name())
                .query((rs, row) -> new CategoryTotal(
                        Category.valueOf(rs.getString("category")),
                        rs.getString("currency").trim(),
                        rs.getBigDecimal("total"),
                        rs.getLong("transaction_count")))
                .list();
    }

    public BigDecimal sumByType(UUID ownerId, OffsetDateTime from, OffsetDateTime to, TransactionType type) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0.00)
                FROM finance_transactions
                WHERE owner_id = :ownerId AND occurred_at >= :from AND occurred_at < :to
                  AND transaction_type = :type
                """)
                .param("ownerId", ownerId)
                .param("from", from)
                .param("to", to)
                .param("type", type.name())
                .query(BigDecimal.class)
                .single();
    }

    private FinanceTransaction mapTransaction(ResultSet rs, int row) throws SQLException {
        return new FinanceTransaction(
                rs.getObject("id", UUID.class),
                TransactionType.valueOf(rs.getString("transaction_type")),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getObject("occurred_at", OffsetDateTime.class),
                Category.valueOf(rs.getString("category")),
                rs.getString("merchant"),
                rs.getString("description"));
    }

    public record CategoryTotal(Category category, String currency, BigDecimal total, long transactionCount) {}
}
