package dev.starterkit.ai.web;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.ErrorType;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import java.util.Optional;
import java.util.Set;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sorts AI provider failures into what the user can do about them. The provider SDKs have already
 * retried rate limits and server errors by the time an exception reaches us.
 */
final class ProviderErrors {

    enum Kind {
        /** Rate limited, overloaded, down or timed out: trying again later can work. */
        BUSY,
        /** The provider refused the API key. */
        KEY_REJECTED,
        /** Anything else: wrong model name, invalid request, no credits. */
        FAILED
    }

    private static final Set<ErrorType> BUSY_TYPES = Set.of(
            ErrorType.RATE_LIMIT_ERROR, ErrorType.OVERLOADED_ERROR, ErrorType.API_ERROR, ErrorType.TIMEOUT_ERROR);
    private static final Set<ErrorType> KEY_TYPES = Set.of(ErrorType.AUTHENTICATION_ERROR, ErrorType.PERMISSION_ERROR);

    private ProviderErrors() {}

    static Kind classify(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            Optional<Kind> kind = classifyOne(t);
            if (kind.isPresent()) {
                return kind.get();
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return Kind.FAILED;
    }

    private static Optional<Kind> classifyOne(Throwable t) {
        if (t instanceof AnthropicServiceException e) {
            // Errors inside a stream arrive with the stream's HTTP 200, so the error type decides
            Optional<ErrorType> type = e.errorType();
            if (type.isPresent() && BUSY_TYPES.contains(type.get())) {
                return Optional.of(Kind.BUSY);
            }
            if (type.isPresent() && KEY_TYPES.contains(type.get())) {
                return Optional.of(Kind.KEY_REJECTED);
            }
            return Optional.of(byStatus(e.statusCode()));
        }
        if (t instanceof OpenAIServiceException e) {
            return Optional.of(byStatus(e.statusCode()));
        }
        if (t instanceof RestClientResponseException e) {
            return Optional.of(byStatus(e.getStatusCode().value()));
        }
        if (t instanceof AnthropicIoException || t instanceof OpenAIIoException
                || t instanceof ResourceAccessException || t instanceof TransientAiException) {
            return Optional.of(Kind.BUSY);
        }
        return Optional.empty();
    }

    static Kind byStatus(int status) {
        if (status == 429 || status >= 500) {
            return Kind.BUSY; // includes Anthropic's 529 overloaded
        }
        if (status == 401 || status == 403) {
            return Kind.KEY_REJECTED;
        }
        return Kind.FAILED;
    }
}
