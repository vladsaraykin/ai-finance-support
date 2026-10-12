package com.finance.support.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FinanceTransaction(
        UUID id,
        TransactionType type,
        BigDecimal amount,
        String currency,
        OffsetDateTime occurredAt,
        Category category,
        String merchant,
        String description
) {}
