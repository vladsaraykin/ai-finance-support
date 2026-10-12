package com.finance.support.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RagKnowledgeService {
    private static final int CHUNK_SIZE = 1800;
    private static final int CHUNK_OVERLAP = 200;

    private final RagProperties properties;
    private final OwnerProperties ownerProperties;
    private final ObjectMapper objectMapper;
    private final RestClient inferenceClient;
    private final RestClient qdrantClient;

    public RagKnowledgeService(RagProperties properties, OwnerProperties ownerProperties,
                               ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.ownerProperties = ownerProperties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.inferenceClient = builder.baseUrl(properties.inferenceUrl()).requestFactory(factory).build();
        this.qdrantClient = builder.baseUrl(properties.qdrantUrl()).requestFactory(factory).build();
    }

    public IngestResult ingest(String title, String source, String documentType, String text) {
        requireEnabled();
        if (title == null || title.isBlank() || title.length() > 200) throw new IllegalArgumentException("Название документа должно содержать от 1 до 200 символов.");
        if (text == null || text.isBlank() || text.length() > 100_000) throw new IllegalArgumentException("Текст документа должен содержать от 1 до 100 000 символов.");
        if (documentType == null || !List.of("RULE", "FINANCIAL_GUIDE", "STORE_CATALOG", "PRODUCT_CATALOG", "OTHER").contains(documentType)) {
            throw new IllegalArgumentException("Неизвестный тип документа.");
        }
        ensureCollection();
        List<String> chunks = split(text);
        List<List<Float>> vectors = embed(chunks);
        UUID documentId = UUID.randomUUID();
        ArrayNode points = objectMapper.createArrayNode();
        for (int i = 0; i < chunks.size(); i++) {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("owner_id", ownerProperties.ownerId().toString())
                    .put("document_id", documentId.toString())
                    .put("title", title.strip())
                    .put("source", source == null || source.isBlank() ? title.strip() : source.strip())
                    .put("document_type", documentType)
                    .put("content", chunks.get(i));
            ObjectNode point = objectMapper.createObjectNode().put("id", UUID.randomUUID().toString());
            point.set("vector", floatArray(vectors.get(i)));
            point.set("payload", payload);
            points.add(point);
        }
        ObjectNode body = objectMapper.createObjectNode().set("points", points);
        try {
            qdrantClient.put().uri("/collections/{collection}/points?wait=true", properties.collection())
                    .contentType(MediaType.APPLICATION_JSON).headers(this::setQdrantKey)
                    .body(body).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Не удалось сохранить документ в Qdrant.");
        }
        return new IngestResult(documentId, chunks.size());
    }

    public List<RetrievedDocument> search(String query) {
        requireEnabled();
        ensureCollection();
        try {
            List<Float> vector = embed(List.of(query)).getFirst();
            ObjectNode ownerFilter = objectMapper.createObjectNode().put("key", "owner_id")
                    .set("match", objectMapper.createObjectNode().put("value", ownerProperties.ownerId().toString()));
            ObjectNode request = objectMapper.createObjectNode().set("vector", floatArray(vector));
            request.put("limit", Math.max(properties.resultCount(), properties.candidateCount()));
            request.put("with_payload", true);
            request.set("filter", objectMapper.createObjectNode().set("must", objectMapper.createArrayNode().add(ownerFilter)));
            JsonNode response = qdrantClient.post().uri("/collections/{collection}/points/search", properties.collection())
                    .contentType(MediaType.APPLICATION_JSON).headers(this::setQdrantKey)
                    .body(request).retrieve().body(JsonNode.class);
            List<RetrievedDocument> candidates = readCandidates(response);
            return rerank(query, candidates);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Поиск по базе документов недоступен.");
        }
    }

    private void ensureCollection() {
        try {
            qdrantClient.get().uri("/collections/{collection}", properties.collection())
                    .headers(this::setQdrantKey).retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) throw new IllegalStateException("Не удалось проверить коллекцию Qdrant.");
            ObjectNode vectors = objectMapper.createObjectNode().put("size", properties.vectorSize()).put("distance", "Cosine");
            try {
                qdrantClient.put().uri("/collections/{collection}", properties.collection())
                        .contentType(MediaType.APPLICATION_JSON).headers(this::setQdrantKey)
                        .body(objectMapper.createObjectNode().set("vectors", vectors)).retrieve().toBodilessEntity();
            } catch (RestClientException createException) {
                throw new IllegalStateException("Не удалось создать коллекцию в Qdrant.");
            }
        } catch (RestClientException exception) {
            throw new IllegalStateException("Qdrant недоступен.");
        }
    }

    private List<List<Float>> embed(List<String> texts) {
        try {
            ObjectNode request = objectMapper.createObjectNode().put("model", properties.embeddingModel());
            ArrayNode input = objectMapper.createArrayNode();
            texts.forEach(input::add);
            request.set("input", input);
            JsonNode response = inferenceClient.post().uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON).headers(this::setInferenceKey)
                    .body(request).retrieve().body(JsonNode.class);
            JsonNode data = response == null ? null : response.path("data");
            if (data == null || !data.isArray() || data.size() != texts.size()) throw new IllegalStateException("Embedding API вернул некорректный ответ.");
            List<List<Float>> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.size() != properties.vectorSize()) throw new IllegalStateException("Размер embedding не совпадает с настройкой RAG_VECTOR_SIZE.");
                List<Float> vector = new ArrayList<>(embedding.size());
                embedding.forEach(value -> vector.add((float) value.asDouble()));
                vectors.add(List.copyOf(vector));
            }
            return List.copyOf(vectors);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Inference-сервис embeddings недоступен.");
        }
    }

    private List<RetrievedDocument> readCandidates(JsonNode response) {
        JsonNode result = response == null ? null : response.path("result");
        if (result == null || !result.isArray()) throw new IllegalStateException("Qdrant вернул некорректный результат поиска.");
        List<RetrievedDocument> documents = new ArrayList<>();
        for (JsonNode point : result) {
            JsonNode payload = point.path("payload");
            String content = payload.path("content").asText("");
            if (content.isBlank()) continue;
            documents.add(new RetrievedDocument(payload.path("title").asText("Документ"),
                    payload.path("source").asText(""), content,
                    point.path("score").isNumber() ? point.path("score").asDouble() : 0));
        }
        return List.copyOf(documents);
    }

    private List<RetrievedDocument> rerank(String query, List<RetrievedDocument> candidates) {
        if (candidates.isEmpty()) return List.of();
        try {
            ObjectNode request = objectMapper.createObjectNode().put("model", properties.rerankerModel())
                    .put("query", query).put("top_n", Math.min(properties.resultCount(), candidates.size()))
                    .put("return_documents", false);
            ArrayNode docs = objectMapper.createArrayNode();
            candidates.forEach(document -> docs.add(document.content()));
            request.set("documents", docs);
            JsonNode response = inferenceClient.post().uri("/rerank")
                    .contentType(MediaType.APPLICATION_JSON).headers(this::setInferenceKey)
                    .body(request).retrieve().body(JsonNode.class);
            JsonNode results = response == null ? null : response.path("results");
            if (results == null || !results.isArray()) throw new IllegalStateException("Reranker вернул некорректный ответ.");
            List<RetrievedDocument> reranked = new ArrayList<>();
            for (JsonNode item : results) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size()) continue;
                RetrievedDocument original = candidates.get(index);
                reranked.add(new RetrievedDocument(original.title(), original.source(), original.content(),
                        item.path("relevance_score").asDouble(item.path("score").asDouble(original.score()))));
            }
            if (reranked.isEmpty()) throw new IllegalStateException("Reranker не вернул совпадений.");
            return List.copyOf(reranked);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Reranker временно недоступен.");
        }
    }

    private List<String> split(String text) {
        String normalized = text.replace("\r\n", "\n").strip();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int boundary = normalized.lastIndexOf('\n', end);
                if (boundary > start + CHUNK_SIZE / 2) end = boundary;
                else {
                    boundary = normalized.lastIndexOf(' ', end);
                    if (boundary > start + CHUNK_SIZE / 2) end = boundary;
                }
            }
            chunks.add(normalized.substring(start, end).strip());
            if (end >= normalized.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }

    private ArrayNode floatArray(List<Float> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private void setInferenceKey(org.springframework.http.HttpHeaders headers) {
        if (properties.inferenceApiKey() != null && !properties.inferenceApiKey().isBlank()) headers.setBearerAuth(properties.inferenceApiKey());
    }

    private void setQdrantKey(org.springframework.http.HttpHeaders headers) {
        if (properties.qdrantApiKey() != null && !properties.qdrantApiKey().isBlank()) headers.set("api-key", properties.qdrantApiKey());
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new IllegalStateException("RAG отключён. Задайте RAG_ENABLED=true.");
    }

    public record IngestResult(UUID documentId, int chunksAdded) {}
    public record RetrievedDocument(String title, String source, String content, double score) {}
}
