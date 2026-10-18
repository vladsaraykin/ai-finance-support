package com.finance.support.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.support.api.FinanceController;
import com.finance.support.application.*;
import com.finance.support.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, FinanceController.class})
@org.springframework.boot.context.properties.EnableConfigurationProperties({OwnerProperties.class, AiProperties.class, RagProperties.class})
@Import({SecurityConfiguration.class, CurrentUser.class, FinanceService.class})
class AuthenticationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PASSWORD = "A-test-password-only";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserRepository users;
    @MockitoBean FinanceTransactionRepository transactions;
    @MockitoBean TransactionInterpreter interpreter;

    @BeforeEach
    void users() {
        when(users.find("alice")).thenReturn(Optional.of(new FinanceUser(OWNER, "alice", encoder.encode(PASSWORD), true)));
        when(users.find("bob")).thenReturn(Optional.of(new FinanceUser(OTHER, "bob", encoder.encode(PASSWORD), true)));
    }

    private Token csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new Token((MockHttpSession) result.getRequest().getSession(), body.path("headerName").asText(), body.path("token").asText());
    }

    private Token login(String login) throws Exception {
        Token token = csrf(null);
        String oldSessionId = token.session().getId();
        mvc.perform(post("/api/auth/login").session(token.session()).header(token.header(), token.value())
                        .param("username", login).param("password", PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.login").value(login))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(token.session().getId()).isNotEqualTo(oldSessionId);
        return csrf(token.session());
    }

    @Test
    void anonymousCannotReadFinanceConfigKnowledgeOrAssistant() throws Exception {
        for (String path : List.of("/api/auth/me", "/api/config", "/api/transactions", "/api/reports/monthly",
                "/api/knowledge/documents/file-test", "/api/assistant/chat")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(delete("/api/transactions/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(transactions, interpreter);
    }

    @Test
    void loginRequiresCsrfAndWrongPasswordDoesNotAuthenticate() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "alice").param("password", PASSWORD))
                .andExpect(status().isForbidden());
        Token token = csrf(null);
        mvc.perform(post("/api/auth/login").session(token.session()).header(token.header(), token.value())
                        .param("username", "alice").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(token.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionPersistsAuthenticationAndLogoutInvalidatesIt() throws Exception {
        Token token = login("alice");
        mvc.perform(get("/api/auth/me").session(token.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(OWNER.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(token.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(token.session()).header(token.header(), token.value()))
                .andExpect(status().isNoContent());
        assertThat(token.session().isInvalid()).isTrue();
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateUsesAuthenticatedOwnerAndPreservesExactAmountDescriptionAndPayment() throws Exception {
        Token token = login("bob");
        UUID id = UUID.randomUUID();
        when(transactions.update(eq(OTHER), eq(id), any())).thenAnswer(call -> Optional.of(call.getArgument(2)));
        String body = """
                {"ownerId":"11111111-1111-1111-1111-111111111111", "type":"EXPENSE","amount":"1234.56",
                 "currency":"RUB","occurredAt":"2026-10-19T12:00:00+03:00", "category":"Моя категория",
                 "merchant":"Магазин","description":"Исправленное описание","paymentMethod":"Наличные"}
                """;
        mvc.perform(put("/api/transactions/" + id).session(token.session()).header(token.header(), token.value())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("1234.56"))
                .andExpect(jsonPath("$.description").value("Исправленное описание"))
                .andExpect(jsonPath("$.paymentMethod").value("Наличные"));
        verify(transactions).update(eq(OTHER), eq(id), argThat(value -> value.category().name().equals("Моя категория")));
        verify(transactions, never()).update(eq(OWNER), any(), any());
    }

    @Test
    void foreignOrDeletedTransactionCannotBeModifiedAndCsrfIsRequired() throws Exception {
        Token token = login("bob");
        UUID foreignId = UUID.randomUUID();
        mvc.perform(delete("/api/transactions/" + foreignId).session(token.session()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/transactions/" + foreignId).session(token.session()).header(token.header(), token.value()))
                .andExpect(status().isNotFound());
        when(transactions.softDelete(OTHER, foreignId)).thenReturn(true);
        mvc.perform(delete("/api/transactions/" + foreignId).session(token.session()).header(token.header(), token.value()))
                .andExpect(status().isNoContent());
        verify(transactions, times(2)).softDelete(OTHER, foreignId);
        verify(transactions, never()).softDelete(eq(OWNER), any());
    }

    @Test
    void invalidMoneyAndPaymentAreRejectedBeforePersistence() throws Exception {
        Token token = login("alice");
        String template = """
                {"type":"EXPENSE","amount":"%s","currency":"RUB","occurredAt":"2026-10-19T12:00:00+03:00",
                 "category":"OTHER","description":"Example","paymentMethod":"%s"}
                """;
        for (String body : List.of(template.formatted("-1", "Карта"), template.formatted("1.001", "Карта"), template.formatted("1", "invalid"))) {
            mvc.perform(put("/api/transactions/" + UUID.randomUUID()).session(token.session()).header(token.header(), token.value())
                            .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(transactions);
    }

    private record Token(MockHttpSession session, String header, String value) {}
}
