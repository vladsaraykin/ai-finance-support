package com.finance.support.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConfirmTransactionsRequest(@NotEmpty @Size(max = 20) List<@Valid TransactionDraft> transactions) {}
