package com.finance.support.application;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID id) {
        super("Транзакция не найдена: " + id);
    }
}
