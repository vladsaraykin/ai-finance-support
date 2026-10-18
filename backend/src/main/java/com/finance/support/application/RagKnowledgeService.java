package com.finance.support.application;

import com.finance.support.security.CurrentUser;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagKnowledgeService {
    private final OpenAiRetrievalClient client;
    private final CurrentUser owner;

    public RagKnowledgeService(OpenAiRetrievalClient client, CurrentUser owner) {
        this.client = client;
        this.owner = owner;
    }

    public DocumentStatus ingest(String title, String source, String documentType, String text) {
        if (title == null || title.isBlank() || title.length() > 200) throw new IllegalArgumentException("Название документа должно содержать от 1 до 200 символов.");
        if (source != null && source.length() > 300) throw new IllegalArgumentException("Источник должен содержать не больше 300 символов.");
        if (text == null || text.isBlank() || text.length() > 100_000) throw new IllegalArgumentException("Текст документа должен содержать от 1 до 100 000 символов.");
        if (documentType == null || !List.of("RULE", "FINANCIAL_GUIDE", "STORE_CATALOG", "PRODUCT_CATALOG", "OTHER").contains(documentType)) {
            throw new IllegalArgumentException("Неизвестный тип документа.");
        }
        String id = UUID.randomUUID().toString();
        Map<String, String> attributes = Map.of("owner_id", owner.ownerId().toString(), "document_id", id,
                "title", title.strip(), "source", source == null || source.isBlank() ? title.strip() : source.strip(),
                "document_type", documentType);
        // Include the title in the searchable text, not just in metadata.
        return readStatus(client.upload(id + ".txt", title.strip() + "\n\n" + text.strip(), attributes));
    }

    public DocumentStatus status(String fileId) {
        if (fileId == null || !fileId.matches("file[-_][A-Za-z0-9_-]+")) throw new IllegalArgumentException("Некорректный идентификатор файла.");
        JsonNode response = client.status(fileId);
        if (response == null || !fileId.equals(response.path("id").asText())) {
            throw new IllegalStateException("OpenAI вернул некорректный статус документа.");
        }
        return readStatus(response);
    }

    private DocumentStatus readStatus(JsonNode response) {
        if (response == null) throw new IllegalStateException("OpenAI не вернул статус документа.");
        if (!isOwner(response.path("attributes"))) throw new KnowledgeDocumentNotFoundException();
        String status = response.path("status").asText("");
        if (!List.of("in_progress", "completed", "failed", "cancelled").contains(status)) {
            throw new IllegalStateException("OpenAI вернул неизвестный статус индексации.");
        }
        String error = switch (status) {
            case "failed" -> "OpenAI не смог проиндексировать документ. Проверьте текст и повторите загрузку.";
            case "cancelled" -> "Индексация документа отменена.";
            default -> null;
        };
        return new DocumentStatus(response.path("attributes").path("document_id").asText(),
                response.path("id").asText(), status, error);
    }

    public List<RetrievedDocument> search(String query) {
        if (query == null || query.isBlank() || query.length() > 2000) throw new IllegalArgumentException("Вопрос должен содержать от 1 до 2000 символов.");
        JsonNode response = client.search(query.strip(), owner.ownerId().toString());
        if (response == null || !response.path("data").isArray()) throw new IllegalStateException("OpenAI вернул некорректный результат поиска.");
        List<RetrievedDocument> results = new ArrayList<>();
        for (JsonNode hit : response.path("data")) {
            JsonNode attributes = hit.path("attributes");
            // Defense in depth: do not trust an upstream search response to enforce ownership.
            if (!isOwner(attributes)) continue;
            StringBuilder content = new StringBuilder();
            for (JsonNode part : hit.path("content")) {
                if ("text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                    if (!content.isEmpty()) content.append('\n');
                    content.append(part.path("text").asText());
                }
            }
            double score = hit.path("score").asDouble(Double.NaN);
            if (content.toString().isBlank() || !Double.isFinite(score) || score < 0 || score > 1) continue;
            results.add(new RetrievedDocument(attributes.path("title").asText("Документ"),
                    attributes.path("source").asText(""), content.toString(), score));
        }
        return List.copyOf(results);
    }

    private boolean isOwner(JsonNode attributes) {
        return owner.ownerId().toString().equals(attributes.path("owner_id").asText());
    }

    public record DocumentStatus(String documentId, String fileId, String status, String error) {}
    public record RetrievedDocument(String title, String source, String content, double score) {}
}
