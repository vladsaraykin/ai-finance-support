package com.finance.support.api;

import java.util.List;

public record MonthlyReportView(
        String month,
        String currency,
        String incomeTotal,
        String expenseTotal,
        String balance,
        long transactionCount,
        List<CategoryTotalView> expensesByCategory
) {
    public record CategoryTotalView(String category, String total, long transactionCount) {}
}
