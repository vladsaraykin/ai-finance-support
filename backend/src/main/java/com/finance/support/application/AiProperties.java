package com.finance.support.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(boolean enabled, String apiKey, String model, String baseUrl) {}
