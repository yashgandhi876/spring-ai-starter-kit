package dev.starterkit.ai.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Stops startup when the chosen provider has no API key. Without this the app starts, and every
 * chat fails later with a 401 from the provider, shown to users as "AI request failed".
 */
@Component
class ApiKeyCheck implements InitializingBean {

    /** The placeholder application.yml uses when the key's environment variable is not set. */
    static final String NOT_SET = "not-set";

    private final Environment environment;

    ApiKeyCheck(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> missing = missingKeys(environment);
        if (!missing.isEmpty()) {
            throw new MissingApiKeyException(missing);
        }
    }

    /** Names of the environment variables that the current provider choice needs but are not set. */
    static List<String> missingKeys(Environment env) {
        String chat = env.getProperty("spring.ai.model.chat", "ollama");
        String embedding = env.getProperty("spring.ai.model.embedding", "ollama");
        Set<String> missing = new LinkedHashSet<>();
        if ("anthropic".equals(chat) && isMissing(env.getProperty("spring.ai.anthropic.api-key"))) {
            missing.add("ANTHROPIC_API_KEY");
        }
        if (("openai".equals(chat) || "openai".equals(embedding))
                && isMissing(env.getProperty("spring.ai.openai.api-key"))) {
            missing.add("OPENAI_API_KEY");
        }
        return new ArrayList<>(missing);
    }

    private static boolean isMissing(String key) {
        return key == null || key.isBlank() || NOT_SET.equals(key.strip());
    }

    /** Thrown at startup; MissingApiKeyFailureAnalyzer turns it into a short message. */
    static class MissingApiKeyException extends RuntimeException {

        private final List<String> variables;

        MissingApiKeyException(List<String> variables) {
            super("Missing API key: " + String.join(", ", variables));
            this.variables = List.copyOf(variables);
        }

        List<String> variables() {
            return variables;
        }
    }
}
