package com.finance.support.api;

import java.math.BigDecimal;
import java.util.List;

public record AssistantResponse(String answer, List<SourceView> sources) {
    public record SourceView(String title, String excerpt, BigDecimal score) {}
}
