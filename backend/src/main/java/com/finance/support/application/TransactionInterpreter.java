package com.finance.support.application;

import com.finance.support.api.TransactionDraft;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionInterpreter {
    private final AiProperties aiProperties;
    private final TransactionTextParser rulesParser;
    private final OpenAiTransactionExtractor openAiExtractor;

    public TransactionInterpreter(AiProperties aiProperties, TransactionTextParser rulesParser,
                                  OpenAiTransactionExtractor openAiExtractor) {
        this.aiProperties = aiProperties;
        this.rulesParser = rulesParser;
        this.openAiExtractor = openAiExtractor;
    }

    public Interpretation interpret(String text) {
        if (aiProperties.enabled()) {
            return openAiExtractor.extract(text);
        }
        return new Interpretation(rulesParser.parse(text), null);
    }

    public record Interpretation(List<TransactionDraft> transactions, String clarificationQuestion) {
        public Interpretation {
            transactions = List.copyOf(transactions);
            if (transactions.size() > 20) throw new IllegalArgumentException("За одно сообщение можно добавить не больше 20 операций.");
            for (TransactionDraft transaction : transactions) {
                if (transaction.amount() == null || transaction.amount().signum() <= 0 || transaction.amount().scale() > 2
                        || transaction.amount().precision() > 19) {
                    throw new IllegalArgumentException("Распознана некорректная сумма. Исправьте текст и повторите.");
                }
                if (!"RUB".equals(transaction.currency())) {
                    throw new IllegalArgumentException("В MVP поддерживается только RUB.");
                }
                if (transaction.type() == null || transaction.category() == null || transaction.occurredAt() == null
                        || transaction.description() == null || transaction.description().length() > 500
                        || transaction.merchant() != null && transaction.merchant().length() > 160) {
                    throw new IllegalArgumentException("Не удалось распознать тип, категорию или дату операции.");
                }
            }
            if (transactions.isEmpty() && (clarificationQuestion == null || clarificationQuestion.isBlank())) {
                clarificationQuestion = "Не удалось выделить операцию. Укажите сумму и назначение расхода или дохода.";
            }
        }
    }
}
