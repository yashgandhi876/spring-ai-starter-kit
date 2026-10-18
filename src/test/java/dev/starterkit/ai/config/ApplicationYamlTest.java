package dev.starterkit.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Guards provider settings in application.yml that a provider would reject at request time. */
class ApplicationYamlTest {

    private static PropertySource<?> yaml;

    @BeforeAll
    static void load() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        yaml = sources.getFirst();
    }

    @Test
    void doesNotSendTemperatureToAnthropic() {
        // Claude Sonnet 5.5 and Opus 5.5 answer any request with a temperature with HTTP 400,
        // which the app reports as 502 "AI request failed" on every chat.
        assertThat(yaml.getProperty("spring.ai.anthropic.chat.options.temperature")).isNull();
        assertThat(yaml.getProperty("spring.ai.anthropic.chat.options.model")).isNotNull();
    }

    @Test
    void givesClaudeRoomToThinkBeforeAnswering() {
        // Thinking counts toward max_tokens on current Claude models; the shared 1024 would cut answers short
        assertThat(yaml.getProperty("spring.ai.anthropic.chat.options.max-tokens"))
                .hasToString("${ANTHROPIC_MAX_TOKENS:8192}");
    }

    @Test
    void stillSendsTemperatureToOllamaAndOpenAi() {
        assertThat(yaml.getProperty("spring.ai.ollama.chat.options.temperature")).hasToString("${AI_TEMPERATURE:0.3}");
        assertThat(yaml.getProperty("spring.ai.openai.chat.options.temperature")).hasToString("${AI_TEMPERATURE:0.3}");
    }
}
