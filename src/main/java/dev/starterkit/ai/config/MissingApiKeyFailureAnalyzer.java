package dev.starterkit.ai.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Explains a missing provider API key at startup instead of printing a stack trace. */
public class MissingApiKeyFailureAnalyzer extends AbstractFailureAnalyzer<ApiKeyCheck.MissingApiKeyException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ApiKeyCheck.MissingApiKeyException cause) {
        String variables = String.join(" and ", cause.variables());
        String description = "The selected AI provider needs " + variables + ", but it is not set for this app.";
        String action = """
                Export it in the same terminal before starting the app, for example:
                    export %s=...
                    ./gradlew bootRun
                With Docker Compose, put it in the .env file instead.
                To run without an API key, use the local model: AI_PROVIDER=ollama and EMBEDDING_PROVIDER=ollama."""
                .formatted(cause.variables().getFirst());
        return new FailureAnalysis(description, action, cause);
    }
}
