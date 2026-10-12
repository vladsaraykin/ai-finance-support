package com.finance.support.api;

import com.finance.support.application.FinanceService;
import com.finance.support.application.AiProperties;
import com.finance.support.application.OwnerProperties;
import com.finance.support.application.RagProperties;
import com.finance.support.application.TransactionInterpreter;
import com.finance.support.domain.Category;
import com.finance.support.domain.FinanceTransaction;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FinanceController {
    private final FinanceService financeService;
    private final TransactionInterpreter interpreter;
    private final OwnerProperties properties;
    private final AiProperties aiProperties;
    private final RagProperties ragProperties;

    public FinanceController(FinanceService financeService, TransactionInterpreter interpreter,
                             OwnerProperties properties, AiProperties aiProperties, RagProperties ragProperties) {
        this.financeService = financeService;
        this.interpreter = interpreter;
        this.properties = properties;
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
    }

    @PostMapping("/transactions/parse")
    public TransactionInterpretationView parse(@Valid @RequestBody ParseTextRequest request) {
        TransactionInterpreter.Interpretation result = interpreter.interpret(request.text());
        return new TransactionInterpretationView(result.transactions().stream().map(TransactionDraftView::from).toList(),
                result.clarificationQuestion());
    }

    @PostMapping("/transactions")
    public List<TransactionView> create(
            @Valid @RequestBody ConfirmTransactionsRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return financeService.create(request.transactions(), idempotencyKey).stream().map(TransactionView::from).toList();
    }

    @GetMapping("/transactions")
    public List<TransactionView> list(@RequestParam(required = false) String month) {
        YearMonth targetMonth = month == null || month.isBlank()
                ? FinanceService.currentMonth(properties.timezone())
                : YearMonth.parse(month);
        return financeService.list(targetMonth).stream().map(TransactionView::from).toList();
    }

    @PutMapping("/transactions/{id}")
    public TransactionView update(@PathVariable UUID id, @Valid @RequestBody TransactionDraft request) {
        return TransactionView.from(financeService.update(id, request));
    }

    @GetMapping("/reports/monthly")
    public MonthlyReportView monthlyReport(@RequestParam(required = false) String month) {
        YearMonth targetMonth = month == null || month.isBlank()
                ? FinanceService.currentMonth(properties.timezone())
                : YearMonth.parse(month);
        return financeService.report(targetMonth);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return Arrays.stream(Category.values()).map(Enum::name).toList();
    }

    @GetMapping("/config")
    public AppConfigView config() {
        return new AppConfigView(aiProperties.enabled(), aiProperties.enabled() && ragProperties.enabled());
    }

    public record AppConfigView(boolean aiEnabled, boolean assistantEnabled) {}
}
