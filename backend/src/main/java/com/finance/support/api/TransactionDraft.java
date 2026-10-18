package com.finance.support.api;

import com.finance.support.domain.Category;
import com.finance.support.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionDraft(
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull OffsetDateTime occurredAt,
        @NotNull Category category,
        @Size(max = 160) String merchant,
        @Size(max = 500) String description,
        @Pattern(regexp = "Карта|Наличные|Перевод|Другое") String paymentMethod
) {
    public TransactionDraft(TransactionType type, BigDecimal amount, String currency, OffsetDateTime occurredAt,
                            Category category, String merchant, String description) {
        this(type, amount, currency, occurredAt, category, merchant, description, "Карта");
    }
}
