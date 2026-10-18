package dev.starterkit.ai.chat;

import org.springframework.ai.chat.metadata.Usage;

/** Tokens one AI call used. Providers that don't report usage give zeros. */
public record TokenUsage(long inputTokens, long outputTokens) {

    public static final TokenUsage NONE = new TokenUsage(0, 0);

    public long total() {
        return inputTokens + outputTokens;
    }

    static TokenUsage from(Usage usage) {
        if (usage == null) {
            return NONE;
        }
        return new TokenUsage(orZero(usage.getPromptTokens()), orZero(usage.getCompletionTokens()));
    }

    /**
     * Streams report usage on some chunks only, and some providers repeat a running total, so keep
     * the largest count seen for each side rather than adding them up.
     */
    TokenUsage max(TokenUsage other) {
        return new TokenUsage(Math.max(inputTokens, other.inputTokens), Math.max(outputTokens, other.outputTokens));
    }

    private static long orZero(Integer n) {
        return n == null ? 0 : n;
    }
}
