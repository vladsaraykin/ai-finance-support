package com.finance.support.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(boolean enabled, String vectorStoreId,
                            @Min(1) @Max(50) int resultCount,
                            @DecimalMin("0.0") @DecimalMax("1.0") double scoreThreshold) {}
