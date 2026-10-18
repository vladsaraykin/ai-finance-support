package com.finance.support.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiRetrievalClientTest {
    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String BASE = "https://api.openai.test/v1";
    private MockRestServiceServer server;
    private RagKnowledgeService service;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        service = service(true, "test-key", "vs_test");
    }

    private RagKnowledgeService service(boolean enabled, String key, String store) {
        var client = new OpenAiRetrievalClient(new AiProperties(false, key, "unused", BASE),
                new RagProperties(enabled, store, 5, 0.2), new ObjectMapper(), restClient);
        var current = org.mockito.Mockito.mock(com.finance.support.security.CurrentUser.class);
        org.mockito.Mockito.when(current.ownerId()).thenReturn(UUID.fromString(OWNER));
        return new RagKnowledgeService(client, current);
    }

    @Test
    void uploadUsesUtf8FileAndOwnerAttributesAndDoesNotClaimIndexingComplete() {
        server.expect(requestTo(BASE + "/files")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("assistants")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Аренда корта")))
                .andRespond(withSuccess("{\"id\":\"file-test\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/vector_stores/vs_test/files")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.file_id").value("file-test"))
                .andExpect(jsonPath("$.attributes.owner_id").value(OWNER))
                .andExpect(jsonPath("$.attributes.document_id").isNotEmpty())
                .andExpect(jsonPath("$.attributes.title").value("Теннис"))
                .andExpect(jsonPath("$.attributes.source").value("Теннис"))
                .andExpect(jsonPath("$.attributes.document_type").value("RULE"))
                .andRespond(withSuccess(status("in_progress", OWNER), MediaType.APPLICATION_JSON));
        var result = service.ingest("Теннис", "", "RULE", "Аренда корта — теннис");
        assertThat(result.fileId()).isEqualTo("file-test");
        assertThat(result.status()).isEqualTo("in_progress");
        assertThat(result.error()).isNull();
        server.verify();
    }

    @Test
    void searchSendsOwnerFilterAndRerankingAndDropsForeignResults() {
        server.expect(requestTo(BASE + "/vector_stores/vs_test/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.query").value("корт"))
                .andExpect(jsonPath("$.max_num_results").value(5))
                .andExpect(jsonPath("$.filters.type").value("eq"))
                .andExpect(jsonPath("$.filters.key").value("owner_id"))
                .andExpect(jsonPath("$.filters.value").value(OWNER))
                .andExpect(jsonPath("$.ranking_options.ranker").value("auto"))
                .andExpect(jsonPath("$.ranking_options.score_threshold").value(0.2))
                .andRespond(withSuccess("""
                        {"data":[
                          {"attributes":{"owner_id":"%s","title":"Правило","source":"Мои правила"},
                           "score":0.91,"content":[{"type":"text","text":"Корт — теннис"},{"type":"text","text":"Инвентарь тоже"}]},
                          {"attributes":{"owner_id":"someone-else"},"score":1,"content":[{"type":"text","text":"private"}]},
                          {"score":1,"content":[{"type":"text","text":"unscoped"}]}
                        ]}
                        """.formatted(OWNER), MediaType.APPLICATION_JSON));
        var results = service.search(" корт ");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().content()).isEqualTo("Корт — теннис\nИнвентарь тоже");
        assertThat(results.getFirst().title()).isEqualTo("Правило");
        assertThat(results.getFirst().score()).isEqualTo(0.91);
        server.verify();
    }

    @Test
    void emptySearchIsValidButMalformedResponseIsNot() {
        server.expect(requestTo(BASE + "/vector_stores/vs_test/search"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/vector_stores/vs_test/search"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(service.search("корт")).isEmpty();
        assertThatThrownBy(() -> service.search("корт")).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void statusTracksCompletionAndSanitizesIndexingFailure() {
        server.expect(requestTo(BASE + "/vector_stores/vs_test/files/file-test"))
                .andRespond(withSuccess(status("completed", OWNER), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/vector_stores/vs_test/files/file-test"))
                .andRespond(withSuccess(status("failed", OWNER), MediaType.APPLICATION_JSON));
        assertThat(service.status("file-test").status()).isEqualTo("completed");
        assertThat(service.status("file-test").error()).contains("не смог").doesNotContain("secret");
        server.verify();
    }

    @Test
    void statusRejectsForeignOrMissingOwnerAndMissingFiles() {
        server.expect(requestTo(BASE + "/vector_stores/vs_test/files/file-test"))
                .andRespond(withSuccess(status("completed", "someone-else"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/vector_stores/vs_test/files/file-test"))
                .andRespond(withSuccess("{\"id\":\"file-test\",\"status\":\"completed\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/vector_stores/vs_test/files/file-test"))
                .andRespond(withResourceNotFound());
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.status("file-test")).isInstanceOf(KnowledgeDocumentNotFoundException.class);
        }
        server.verify();
    }

    @Test
    void failedAttachmentCleansUpOnlyUploadedFile() {
        server.expect(requestTo(BASE + "/files"))
                .andRespond(withSuccess("{\"id\":\"file-test\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/vector_stores/vs_test/files"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("secret provider details"));
        server.expect(requestTo(BASE + "/files/file-test")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());
        assertThatThrownBy(() -> service.ingest("Title", null, "RULE", "Content"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret");
        server.verify();
    }

    @Test
    void upstreamErrorsDoNotExposeProviderBodies() {
        server.expect(requestTo(BASE + "/vector_stores/vs_test/search"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("secret provider details"));
        assertThatThrownBy(() -> service.search("корт"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret");
        server.verify();
    }

    @Test
    void disabledOrIncompleteConfigurationMakesNoNetworkRequests() {
        assertThatThrownBy(() -> service(false, "key", "vs_test").search("корт")).hasMessageContaining("RAG отключён");
        assertThatThrownBy(() -> service(true, "", "vs_test").search("корт")).hasMessageContaining("OPENAI_API_KEY");
        assertThatThrownBy(() -> service(true, "key", "").search("корт")).hasMessageContaining("OPENAI_VECTOR_STORE_ID");
        server.verify();
    }

    @Test
    void invalidInputMakesNoNetworkRequests() {
        assertThatThrownBy(() -> service.ingest("", null, "RULE", "content")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ingest("title", null, "UNKNOWN", "content")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ingest("title", null, "RULE", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.status("../files")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    private String status(String status, String owner) {
        return """
                {"id":"file-test","status":"%s","attributes":{"owner_id":"%s","document_id":"doc-test"},
                 "last_error":{"message":"secret provider details"}}
                """.formatted(status, owner);
    }
}
