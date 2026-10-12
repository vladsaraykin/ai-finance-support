package com.finance.support.application;

import com.finance.support.api.AssistantResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AssistantService {
    private final AiProperties aiProperties;
    private final RagProperties ragProperties;
    private final RagKnowledgeService ragKnowledgeService;
    private final OpenAiAssistantClient openAiAssistantClient;

    public AssistantService(AiProperties aiProperties, RagProperties ragProperties,
                            RagKnowledgeService ragKnowledgeService, OpenAiAssistantClient openAiAssistantClient) {
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
        this.ragKnowledgeService = ragKnowledgeService;
        this.openAiAssistantClient = openAiAssistantClient;
    }

    public AssistantResponse answer(String message) {
        if (!aiProperties.enabled() || !ragProperties.enabled()) {
            throw new IllegalStateException("AI-помощник с RAG не включён. Проверьте AI_ENABLED и RAG_ENABLED.");
        }
        List<RagKnowledgeService.RetrievedDocument> documents = ragKnowledgeService.search(message);
        String answer = openAiAssistantClient.answer(message, documents);
        List<AssistantResponse.SourceView> sources = documents.stream()
                .map(document -> new AssistantResponse.SourceView(document.title(),
                        document.content().substring(0, Math.min(500, document.content().length())),
                        BigDecimal.valueOf(document.score())))
                .toList();
        return new AssistantResponse(answer, sources);
    }
}
