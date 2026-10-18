package dev.starterkit.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

class ApiKeyCheckTest {

    @Test
    void ollamaNeedsNoKey() {
        assertThat(ApiKeyCheck.missingKeys(new MockEnvironment())).isEmpty();
    }

    @Test
    void anthropicWithThePlaceholderKeyStopsStartup() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "anthropic")
                .withProperty("spring.ai.anthropic.api-key", ApiKeyCheck.NOT_SET);

        assertThatThrownBy(() -> new ApiKeyCheck(env).afterPropertiesSet())
                .isInstanceOf(ApiKeyCheck.MissingApiKeyException.class)
                .hasMessage("Missing API key: ANTHROPIC_API_KEY");
    }

    @Test
    void blankKeysCountAsMissing() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "anthropic")
                .withProperty("spring.ai.anthropic.api-key", "  ");

        assertThat(ApiKeyCheck.missingKeys(env)).containsExactly("ANTHROPIC_API_KEY");
    }

    @Test
    void anthropicChatWithOpenAiEmbeddingsNeedsBothKeys() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "anthropic")
                .withProperty("spring.ai.model.embedding", "openai");

        assertThat(ApiKeyCheck.missingKeys(env)).containsExactly("ANTHROPIC_API_KEY", "OPENAI_API_KEY");
    }

    @Test
    void openAiChatAndEmbeddingsAskForTheKeyOnce() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "openai");

        assertThat(ApiKeyCheck.missingKeys(env)).containsExactly("OPENAI_API_KEY");
    }

    @Test
    void startsWhenTheKeyIsSet() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "anthropic")
                .withProperty("spring.ai.anthropic.api-key", "sk-ant-test");

        new ApiKeyCheck(env).afterPropertiesSet();
        assertThat(ApiKeyCheck.missingKeys(env)).isEmpty();
    }

    @Test
    void failureMessageSaysWhichVariableToExport() {
        FailureAnalysis analysis = new MissingApiKeyFailureAnalyzer()
                .analyze(new IllegalStateException("Error creating bean 'apiKeyCheck'",
                        new ApiKeyCheck.MissingApiKeyException(java.util.List.of("ANTHROPIC_API_KEY"))));

        assertThat(analysis.getDescription()).contains("ANTHROPIC_API_KEY");
        assertThat(analysis.getAction()).contains("export ANTHROPIC_API_KEY=", "AI_PROVIDER=ollama");
    }
}
