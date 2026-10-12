package com.finance.support.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(boolean enabled, String inferenceUrl, String inferenceApiKey,
                            String qdrantUrl, String qdrantApiKey, String collection,
                            String embeddingModel, String rerankerModel, int vectorSize,
                            int candidateCount, int resultCount) {}
