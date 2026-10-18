package com.finance.support.api;

import java.time.OffsetDateTime;

public record TransactionDraftView(
        String type,
        String amount,
        String currency,
        OffsetDateTime occurredAt,
        String category,
        String merchant,
        String description,
        String paymentMethod
) {
    public static TransactionDraftView from(TransactionDraft draft) {
        return new TransactionDraftView(draft.type().name(), draft.amount().toPlainString(), draft.currency(),
                draft.occurredAt(), draft.category().name(), draft.merchant(), draft.description(), draft.paymentMethod());
    }
}
