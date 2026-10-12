package com.finance.support.api;

import java.util.List;

public record TransactionInterpretationView(List<TransactionDraftView> transactions, String clarificationQuestion) {}
