package com.finance.support.application;

public class KnowledgeDocumentNotFoundException extends RuntimeException {
    public KnowledgeDocumentNotFoundException() {
        super("Документ не найден.");
    }
}
