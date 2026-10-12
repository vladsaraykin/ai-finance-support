package com.finance.support.api;

import com.finance.support.domain.FinanceTransaction;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionView(
        UUID id,
        String type,
        String amount,
        String currency,
        OffsetDateTime occurredAt,
        String category,
        String merchant,
        String description
) {
    public static TransactionView from(FinanceTransaction transaction) {
        return new TransactionView(transaction.id(), transaction.type().name(), transaction.amount().toPlainString(),
                transaction.currency(), transaction.occurredAt(), transaction.category().name(),
                transaction.merchant(), transaction.description());
    }
}
