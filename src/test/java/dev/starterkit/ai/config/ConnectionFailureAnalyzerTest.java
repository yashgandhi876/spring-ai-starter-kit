package dev.starterkit.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

class ConnectionFailureAnalyzerTest {

    private final ConnectionFailureAnalyzer analyzer = new ConnectionFailureAnalyzer();

    @Test
    void explainsOllamaNotRunning() {
        Throwable failure = new IllegalStateException("Error creating bean 'chatClient'",
                new RuntimeException("I/O error on GET request for \"http://localhost:11434/api/tags\"",
                        new ConnectException("Connection refused")));

        FailureAnalysis analysis = analyzer.analyze(failure);

        assertThat(analysis.getDescription()).isEqualTo(ConnectionFailureAnalyzer.OLLAMA_DESCRIPTION);
        assertThat(analysis.getAction()).contains("docker compose up -d", "AI_PROVIDER=anthropic", "OLLAMA_BASE_URL");
    }

    @Test
    void explainsDatabaseNotRunning() {
        Throwable failure = new IllegalStateException("Failed to obtain JDBC Connection",
                new RuntimeException("org.postgresql.util.PSQLException: Connection to localhost:5432 refused",
                        new ConnectException("Connection refused")));

        FailureAnalysis analysis = analyzer.analyze(failure);

        assertThat(analysis.getDescription()).isEqualTo(ConnectionFailureAnalyzer.DATABASE_DESCRIPTION);
        assertThat(analysis.getAction()).contains("DB_URL");
    }

    @Test
    void leavesOtherConnectionFailuresToSpringBoot() {
        Throwable failure = new IllegalStateException("something else", new ConnectException("Connection refused"));

        assertThat(analyzer.analyze(failure)).isNull();
    }
}
