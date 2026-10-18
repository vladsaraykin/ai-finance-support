package com.finance.support.api;

import com.finance.support.application.AiProperties;
import com.finance.support.application.OwnerProperties;
import com.finance.support.application.RagProperties;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceConfigTest {
    private FinanceController.AppConfigView config(String owner, String store, boolean enabled) {
        var current = org.mockito.Mockito.mock(com.finance.support.security.CurrentUser.class);
        org.mockito.Mockito.when(current.ownerId()).thenReturn(UUID.fromString(owner));
        return new FinanceController(null, null, new OwnerProperties(UUID.fromString(owner), ZoneId.of("Europe/Moscow")),
                new AiProperties(false, "secret", "unused", "https://api.openai.test/v1"),
                new RagProperties(enabled, store, 5, 0), current).config();
    }

    @Test
    void importScopeChangesWhenOwnerOrVectorStoreChanges() {
        String owner = "11111111-1111-1111-1111-111111111111";
        String scope = config(owner, "vs_test", true).ragScope();
        assertThat(config(owner, "vs_test", true).ragScope()).isEqualTo(scope);
        assertThat(config(owner, "vs_other", true).ragScope()).isNotEqualTo(scope);
        assertThat(config("22222222-2222-2222-2222-222222222222", "vs_test", true).ragScope()).isNotEqualTo(scope);
        assertThat(scope).doesNotContain("secret", "vs_test");
    }

    @Test
    void documentImportCanBeEnabledWithoutAnswerGeneration() {
        var config = config("11111111-1111-1111-1111-111111111111", "vs_test", true);
        assertThat(config.ragEnabled()).isTrue();
        assertThat(config.assistantEnabled()).isFalse();
        assertThat(config("11111111-1111-1111-1111-111111111111", "vs_test", false).ragScope()).isNull();
    }
}
