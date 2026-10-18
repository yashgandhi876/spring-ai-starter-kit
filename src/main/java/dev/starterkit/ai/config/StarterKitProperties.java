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

    @Valid
    private final Security security = new Security();

    @Valid
    private final Usage usage = new Usage();

    public Branding getBranding() {
        return branding;
    }

    public Prompts getPrompts() {
        return prompts;
    }

    public Rag getRag() {
        return rag;
    }

    public Security getSecurity() {
        return security;
    }

    public Usage getUsage() {
        return usage;
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

    /** Login. Off by default so the quick start needs no setup; turn it on before exposing the app. */
    public static class Security {

        /** When true, every page and API call needs a signed-in user. */
        private boolean enabled = false;

        /** The first admin, created at startup if no user with this name exists yet. */
        @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "must be 3 to 50 letters, digits, dots, dashes or underscores")
        private String adminUsername = "admin";

        /** Only used to create the first admin. Required, at least 12 characters, when login is on. */
        private String adminPassword = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }

    /** Per-user (or per-IP when login is off) daily limits, and the prices used for cost tracking. */
    public static class Usage {

        /** AI requests (chat, stream, ask) allowed per user per day, counted in UTC. 0 means no limit. */
        @Min(0)
        private int dailyRequestLimit = 0;

        /** Input plus output tokens allowed per user per day. 0 means no limit. */
        @Min(0)
        private long dailyTokenLimit = 0;

        /**
         * US dollars per million input tokens. Empty uses the built-in price for known models
         * (see PriceList); set it when you use another model or your price differs.
         */
        @DecimalMin("0.0")
        private Double priceInputPerMtok;

        /** US dollars per million output tokens. Empty uses the built-in price for known models. */
        @DecimalMin("0.0")
        private Double priceOutputPerMtok;

        public int getDailyRequestLimit() {
            return dailyRequestLimit;
        }

        public void setDailyRequestLimit(int dailyRequestLimit) {
            this.dailyRequestLimit = dailyRequestLimit;
        }

        public long getDailyTokenLimit() {
            return dailyTokenLimit;
        }

        public void setDailyTokenLimit(long dailyTokenLimit) {
            this.dailyTokenLimit = dailyTokenLimit;
        }

        public Double getPriceInputPerMtok() {
            return priceInputPerMtok;
        }

        public void setPriceInputPerMtok(Double priceInputPerMtok) {
            this.priceInputPerMtok = priceInputPerMtok;
        }

        public Double getPriceOutputPerMtok() {
            return priceOutputPerMtok;
        }

        public void setPriceOutputPerMtok(Double priceOutputPerMtok) {
            this.priceOutputPerMtok = priceOutputPerMtok;
        }
    }
}
