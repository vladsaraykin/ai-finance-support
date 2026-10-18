package com.finance.support.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class OpenAiRetrievalClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiRetrievalClient.class);
    private final RestClient client;
    private final AiProperties ai;
    private final RagProperties rag;
    private final ObjectMapper mapper;

    @Autowired
    public OpenAiRetrievalClient(AiProperties ai, RagProperties rag, ObjectMapper mapper, RestClient.Builder builder) {
        this(ai, rag, mapper, createClient(ai, builder));
    }

    OpenAiRetrievalClient(AiProperties ai, RagProperties rag, ObjectMapper mapper, RestClient client) {
        this.ai = ai;
        this.rag = rag;
        this.mapper = mapper;
        this.client = client;
    }

    private static RestClient createClient(AiProperties ai, RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(45));
        return builder.clone().baseUrl(ai.baseUrl()).requestFactory(factory)
                .defaultHeader("OpenAI-Beta", "assistants=v2")
                .defaultHeaders(headers -> headers.setBearerAuth(ai.apiKey() == null ? "" : ai.apiKey())).build();
    }

    public JsonNode upload(String filename, String text, Map<String, String> attributes) {
        requireReady();
        String fileId = null;
        try {
            var body = new LinkedMultiValueMap<String, Object>();
            body.add("purpose", "assistants");
            body.add("file", new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
                @Override public String getFilename() { return filename; }
            });
            JsonNode file = client.post().uri("/files").contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body).retrieve().body(JsonNode.class);
            fileId = requiredFileId(file);
            JsonNode attached = client.post().uri("/vector_stores/{store}/files", rag.vectorStoreId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("file_id", fileId, "attributes", attributes))
                    .retrieve().body(JsonNode.class);
            if (!fileId.equals(requiredFileId(attached))) throw new IllegalStateException("OpenAI вернул неверный идентификатор документа.");
            return attached;
        } catch (RestClientException | IllegalStateException exception) {
            // Only clean up the file created by this request; never delete a store or existing files.
            if (fileId != null) cleanup(fileId);
            throw new IllegalStateException("Не удалось загрузить документ в OpenAI. Проверьте настройки и повторите позже.");
        }
    }

    public JsonNode status(String fileId) {
        requireReady();
        try {
            return client.get().uri("/vector_stores/{store}/files/{file}", rag.vectorStoreId(), fileId)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) throw new KnowledgeDocumentNotFoundException();
            throw new IllegalStateException("Не удалось проверить индексацию документа в OpenAI.");
        } catch (RestClientException exception) {
            throw new IllegalStateException("Не удалось проверить индексацию документа в OpenAI.");
        }
    }

    public JsonNode search(String query, String ownerId) {
        requireReady();
        var request = mapper.createObjectNode().put("query", query).put("max_num_results", rag.resultCount());
        request.set("filters", mapper.valueToTree(Map.of("type", "eq", "key", "owner_id", "value", ownerId)));
        request.set("ranking_options", mapper.valueToTree(Map.of("ranker", "auto", "score_threshold", rag.scoreThreshold())));
        try {
            return client.post().uri("/vector_stores/{store}/search", rag.vectorStoreId())
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Поиск по документам в OpenAI временно недоступен.");
        }
    }

    private String requiredFileId(JsonNode response) {
        String id = response == null ? "" : response.path("id").asText("");
        if (!id.matches("file[-_][A-Za-z0-9_-]+")) throw new IllegalStateException("OpenAI вернул неверный идентификатор файла.");
        return id;
    }

    private void cleanup(String fileId) {
        try {
            client.delete().uri("/files/{file}", fileId).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Could not remove uploaded OpenAI file {} after attachment failure; remove it manually", fileId);
        }
    }

    private void requireReady() {
        if (!rag.enabled()) throw new IllegalStateException("RAG отключён. Задайте RAG_ENABLED=true.");
        if (ai.apiKey() == null || ai.apiKey().isBlank()) throw new IllegalStateException("Для RAG задайте OPENAI_API_KEY.");
        if (rag.vectorStoreId() == null || !rag.vectorStoreId().matches("vs_[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("Для RAG задайте OPENAI_VECTOR_STORE_ID существующего хранилища.");
        }
    }
}
