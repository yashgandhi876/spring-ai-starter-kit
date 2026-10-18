package dev.starterkit.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything a customer is expected to change without touching code.
 * Set these in application.yml under "starter-kit", or with environment variables
 * (for example STARTER_KIT_RAG_TOP_K=6). Invalid values stop the app at startup with a clear message.
 */
@Validated
@ConfigurationProperties(prefix = "starter-kit")
public class StarterKitProperties {

    @Valid
    private final Branding branding = new Branding();

    @Valid
    private final Prompts prompts = new Prompts();

    @Valid
    private final Rag rag = new Rag();

    public Branding getBranding() {
        return branding;
    }

    public Prompts getPrompts() {
        return prompts;
    }

    public Rag getRag() {
        return rag;
    }

    public static class Branding {

        @NotBlank
        private String appName = "Spring AI Starter Kit";

        @NotBlank
        private String welcomeMessage = "Ask me anything, or upload a document and ask questions about it.";

        /** Hex colour such as #2f6f4f, used for buttons and highlights on the chat page. */
        @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "must be a hex colour like #2f6f4f")
        private String accentColor = "#2f6f4f";

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getWelcomeMessage() {
            return welcomeMessage;
        }

        public void setWelcomeMessage(String welcomeMessage) {
            this.welcomeMessage = welcomeMessage;
        }

        public String getAccentColor() {
            return accentColor;
        }

        public void setAccentColor(String accentColor) {
            this.accentColor = accentColor;
        }
    }

    public static class Prompts {

        @NotBlank
        private String system = """
                You are a helpful assistant inside a Spring Boot application.
                Answer clearly and concisely. If you do not know something, say so.""";

        /** Must contain {context} and {question}; they are replaced at runtime. */
        @NotBlank
        @Pattern(regexp = "(?s).*\\{context}.*", message = "must contain {context}")
        @Pattern(regexp = "(?s).*\\{question}.*", message = "must contain {question}")
        private String rag = """
                Answer the question using only the context below.
                If the context does not contain the answer, say that you could not find it in the documents.

                Context:
                {context}

                Question: {question}""";

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getRag() {
            return rag;
        }

        public void setRag(String rag) {
            this.rag = rag;
        }
    }

    public static class Rag {

        /** How many matching chunks are sent to the model with each question. */
        @Min(1)
        @Max(20)
        private int topK = 4;

        /** 0 accepts every match; closer to 1 keeps only very similar chunks. */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double similarityThreshold = 0.0;

        /**
         * Keep only chunks scoring within this distance of the best match, so a document that is
         * not about the question is neither sent to the model nor cited. 1 keeps every match.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double maxScoreGap = 0.15;

        /** Target chunk size in tokens when splitting documents. */
        @Min(100)
        @Max(4000)
        private int chunkSize = 800;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public double getMaxScoreGap() {
            return maxScoreGap;
        }

        public void setMaxScoreGap(double maxScoreGap) {
            this.maxScoreGap = maxScoreGap;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }
    }
}
