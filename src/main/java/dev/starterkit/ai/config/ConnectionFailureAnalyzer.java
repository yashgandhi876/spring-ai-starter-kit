package dev.starterkit.ai.config;

import java.net.ConnectException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Replaces the long stack trace you get when Ollama or Postgres isn't running at startup
 * with a short message that says what to start or which setting to change.
 */
public class ConnectionFailureAnalyzer extends AbstractFailureAnalyzer<ConnectException> {

    static final String OLLAMA_DESCRIPTION = "Could not reach Ollama, the local AI model server.";
    static final String OLLAMA_ACTION = """
            Start it with 'docker compose up -d' (or install it from https://ollama.com), then run the app again.
            Using Claude or OpenAI instead? Set AI_PROVIDER=anthropic or AI_PROVIDER=openai with its API key.
            Anthropic has no embeddings API, so also set EMBEDDING_PROVIDER=openai, or keep Ollama running for embeddings.
            Compose Ollama on another port? Set OLLAMA_PORT. Ollama on another machine? Set OLLAMA_BASE_URL.""";
    static final String DATABASE_DESCRIPTION = "Could not reach the Postgres database.";
    static final String DATABASE_ACTION = """
            Start it with 'docker compose up -d', then run the app again.
            Compose Postgres on another port (because 5432 was taken)? Set the same DB_PORT for the app.
            Using your own database? Set DB_URL, DB_USER and DB_PASSWORD. It needs the pgvector extension.""";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ConnectException cause) {
        String trace = describe(rootFailure);
        if (trace.contains("11434") || trace.toLowerCase().contains("ollama")) {
            return new FailureAnalysis(OLLAMA_DESCRIPTION, OLLAMA_ACTION, cause);
        }
        if (trace.contains("postgresql") || trace.contains("jdbc") || trace.contains("5432")) {
            return new FailureAnalysis(DATABASE_DESCRIPTION, DATABASE_ACTION, cause);
        }
        return null;
    }

    private static String describe(Throwable failure) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = failure; t != null && sb.length() < 20_000; t = t.getCause()) {
            sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
            if (t.getCause() == t) {
                break;
            }
        }
        return sb.toString();
    }
}
