package dev.starterkit.ai.usage;

import java.time.Duration;

/** The caller used up today's allowance. Retry after the given time, when the UTC day changes. */
public class UsageLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public UsageLimitExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
