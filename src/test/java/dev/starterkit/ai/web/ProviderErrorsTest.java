package dev.starterkit.ai.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.SseException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnexpectedStatusCodeException;
import dev.starterkit.ai.web.ProviderErrors.Kind;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class ProviderErrorsTest {

    private static final Headers NO_HEADERS = Headers.builder().build();

    private static JsonValue errorBody(String type) {
        return JsonValue.from(Map.of("type", "error", "error", Map.of("type", type, "message", "provider says no")));
    }

    @Test
    void anthropicRateLimitOverloadAndServerErrorsAreBusy() {
        assertThat(ProviderErrors.classify(RateLimitException.builder()
                .headers(NO_HEADERS).body(errorBody("rate_limit_error")).build())).isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.classify(UnexpectedStatusCodeException.builder()
                .statusCode(529).headers(NO_HEADERS).body(errorBody("overloaded_error")).build())).isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.classify(InternalServerException.builder()
                .statusCode(500).headers(NO_HEADERS).body(errorBody("api_error")).build())).isEqualTo(Kind.BUSY);
    }

    @Test
    void anthropicOverloadInsideAStreamIsBusyDespiteHttp200() {
        SseException midStream = SseException.builder()
                .statusCode(200).headers(NO_HEADERS).body(errorBody("overloaded_error")).build();

        assertThat(ProviderErrors.classify(midStream)).isEqualTo(Kind.BUSY);
    }

    @Test
    void anthropicInvalidKeyIsKeyRejected() {
        assertThat(ProviderErrors.classify(UnauthorizedException.builder()
                .headers(NO_HEADERS).body(errorBody("authentication_error")).build())).isEqualTo(Kind.KEY_REJECTED);
    }

    @Test
    void anthropicNoCreditsIsAPlainFailure() {
        // The real "credit balance is too low" answer is a 400 invalid_request_error
        assertThat(ProviderErrors.classify(BadRequestException.builder()
                .headers(NO_HEADERS).body(errorBody("invalid_request_error")).build())).isEqualTo(Kind.FAILED);
    }

    @Test
    void networkFailuresAndTimeoutsAreBusy() {
        assertThat(ProviderErrors.classify(new AnthropicIoException("timeout", new SocketTimeoutException())))
                .isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.classify(new ResourceAccessException("I/O error", new IOException())))
                .isEqualTo(Kind.BUSY);
    }

    @Test
    void ollamaAndOpenAiStatusesThroughSpringAi() {
        assertThat(ProviderErrors.classify(new TransientAiException("HTTP 503"))).isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.classify(new NonTransientAiException("wrapped",
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null))))
                .isEqualTo(Kind.KEY_REJECTED);
        assertThat(ProviderErrors.classify(new NonTransientAiException("wrapped",
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null))))
                .isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.classify(new NonTransientAiException("model llama9 not found")))
                .isEqualTo(Kind.FAILED);
    }

    @Test
    void findsTheProviderErrorBehindWrappers() {
        RuntimeException wrapped = new IllegalStateException("outer", new RuntimeException("middle",
                RateLimitException.builder().headers(NO_HEADERS).body(errorBody("rate_limit_error")).build()));

        assertThat(ProviderErrors.classify(wrapped)).isEqualTo(Kind.BUSY);
    }

    @Test
    void statusCodes() {
        assertThat(ProviderErrors.byStatus(429)).isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.byStatus(529)).isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.byStatus(503)).isEqualTo(Kind.BUSY);
        assertThat(ProviderErrors.byStatus(401)).isEqualTo(Kind.KEY_REJECTED);
        assertThat(ProviderErrors.byStatus(403)).isEqualTo(Kind.KEY_REJECTED);
        assertThat(ProviderErrors.byStatus(400)).isEqualTo(Kind.FAILED);
        assertThat(ProviderErrors.byStatus(404)).isEqualTo(Kind.FAILED);
    }

    @Test
    void busyProblemIsServiceUnavailableWithoutProviderText() {
        ProblemDetail problem = ApiExceptionHandler.providerProblem(UnexpectedStatusCodeException.builder()
                .statusCode(529).headers(NO_HEADERS).body(errorBody("overloaded_error")).build());

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getTitle()).isEqualTo("AI provider busy");
        assertThat(problem.getDetail()).contains("try again").doesNotContain("provider says no");
    }

    @Test
    void rejectedKeyProblemSaysToCheckTheKey() {
        ProblemDetail problem = ApiExceptionHandler.providerProblem(UnauthorizedException.builder()
                .headers(NO_HEADERS).body(errorBody("authentication_error")).build());

        assertThat(problem.getStatus()).isEqualTo(502);
        assertThat(problem.getTitle()).isEqualTo("AI provider rejected the API key");
        assertThat(problem.getDetail()).doesNotContain("provider says no");
    }
}
