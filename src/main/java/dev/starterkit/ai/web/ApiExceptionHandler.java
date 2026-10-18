package dev.starterkit.ai.web;

import dev.starterkit.ai.rag.DocumentService.EmptyDocumentException;
import dev.starterkit.ai.rag.DocumentService.UnsupportedFileTypeException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns failures into RFC 9457 problem responses with a message a person can act on.
 * Standard Spring MVC errors (bad JSON, missing file part, 404, upload too large) keep their
 * normal status codes through the base class. Server-side failures are logged in full but never
 * leak stack traces, provider messages or keys to the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Invalid request", detail));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ProblemDetail unsupported(UnsupportedFileTypeException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type", ex.getMessage());
    }

    @ExceptionHandler(EmptyDocumentException.class)
    ProblemDetail empty(EmptyDocumentException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "No text in document", ex.getMessage());
    }

    /** Not a multipart request at all, or a malformed one. Oversized uploads are handled by the base class. */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipart(MultipartException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid upload",
                "Send the file as multipart/form-data in a field named \"file\".");
    }

    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class})
    ProblemDetail aiProvider(RuntimeException ex) {
        log.error("AI provider call failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "AI request failed",
                "The AI provider could not complete the request. Check the API key, model name and provider status.");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database(DataAccessException ex) {
        log.error("Database call failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable",
                "The document store could not be reached. Check that Postgres is running.");
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail unexpected(RuntimeException ex) {
        if (looksLikeProviderError(ex)) {
            return aiProvider(ex);
        }
        log.error("Request failed", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong on the server. Check the server logs for details.");
    }

    /** Provider SDKs throw their own exception types; recognise them by package without depending on each SDK. */
    static boolean looksLikeProviderError(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.startsWith("com.openai.") || name.startsWith("com.anthropic.")
                    || name.startsWith("org.springframework.ai.")
                    || name.startsWith("org.springframework.web.client.")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
