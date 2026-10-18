package com.finance.support.application;

import com.finance.support.security.CurrentUser;

import com.finance.support.api.MonthlyReportView;
import com.finance.support.api.TransactionDraft;
import com.finance.support.domain.FinanceTransaction;
import com.finance.support.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class FinanceService {
    private final FinanceTransactionRepository repository;
    private final OwnerProperties properties;
    private final CurrentUser current;

    public FinanceService(FinanceTransactionRepository repository, OwnerProperties properties, CurrentUser current) {
        this.repository = repository;
        this.properties = properties;
        this.current = current;
    }

    @Transactional
    public List<FinanceTransaction> create(List<TransactionDraft> drafts, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        if (idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("Idempotency-Key должен быть не длиннее 64 символов.");
        }
        String currency = drafts.getFirst().currency();
        if (drafts.stream().anyMatch(draft -> !draft.currency().equals(currency))) {
            throw new IllegalArgumentException("В одной операции добавления должна быть одна валюта.");
        }
        if (!"RUB".equals(currency)) {
            throw new IllegalArgumentException("В MVP поддерживается только RUB.");
        }
        UUID ownerId = current.ownerId();
        String requestKey = idempotencyKey;
        return java.util.stream.IntStream.range(0, drafts.size())
                .mapToObj(index -> repository.save(ownerId, requestKey + ":" + index, fromDraft(drafts.get(index))))
                .toList();
    }

    public List<FinanceTransaction> list(YearMonth month) {
        Period period = period(month);
        return repository.findByPeriod(current.ownerId(), period.from(), period.to());
    }

    public FinanceTransaction update(UUID id, TransactionDraft draft) {
        if (!"RUB".equals(draft.currency())) {
            throw new IllegalArgumentException("В MVP поддерживается только RUB.");
        }
        FinanceTransaction update = new FinanceTransaction(id, draft.type(), draft.amount(), draft.currency(),
                draft.occurredAt(), draft.category(), draft.merchant(), draft.description(), draft.paymentMethod());
        return repository.update(current.ownerId(), id, update)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    public List<String> categories() {
        return java.util.stream.Stream.concat(
                java.util.Arrays.stream(com.finance.support.domain.Category.values()).map(com.finance.support.domain.Category::name),
                repository.categories(current.ownerId()).stream()).distinct().toList();
    }

    public void delete(UUID id) {
        if (!repository.softDelete(current.ownerId(), id)) throw new TransactionNotFoundException(id);
    }

    public MonthlyReportView report(YearMonth month) {
        Period period = period(month);
        BigDecimal income = repository.sumByType(current.ownerId(), period.from(), period.to(), TransactionType.INCOME);
        BigDecimal expenses = repository.sumByType(current.ownerId(), period.from(), period.to(), TransactionType.EXPENSE);
        long transactionCount = list(month).size();
        List<MonthlyReportView.CategoryTotalView> categories = repository.categoryTotals(
                        current.ownerId(), period.from(), period.to(), TransactionType.EXPENSE)
                .stream()
                .map(item -> new MonthlyReportView.CategoryTotalView(item.category().name(),
                        item.total().toPlainString(), item.transactionCount()))
                .toList();
        return new MonthlyReportView(month.toString(), "RUB", income.toPlainString(), expenses.toPlainString(),
                income.subtract(expenses).toPlainString(), transactionCount, categories);
    }

    private FinanceTransaction fromDraft(TransactionDraft draft) {
        if (draft.amount().signum() <= 0) throw new IllegalArgumentException("Сумма должна быть больше нуля.");
        return new FinanceTransaction(UUID.randomUUID(), draft.type(), draft.amount().setScale(2), draft.currency(),
                draft.occurredAt(), draft.category(), draft.merchant(), draft.description(),
                draft.paymentMethod() == null ? "Карта" : draft.paymentMethod());
    }

    private Period period(YearMonth month) {
        ZoneId zone = properties.timezone();
        return new Period(month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime());
    }

    public static YearMonth currentMonth(ZoneId zone) {
        return YearMonth.from(LocalDate.now(zone));
    }

    private record Period(java.time.OffsetDateTime from, java.time.OffsetDateTime to) {}
}
