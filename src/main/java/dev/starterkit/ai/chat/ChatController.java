package dev.starterkit.ai.chat;

import dev.starterkit.ai.config.StarterKitProperties;
import dev.starterkit.ai.web.ApiExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import dev.starterkit.ai.usage.UsageService;
import dev.starterkit.ai.usage.UsageSubjects;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class ChatController {

    static final int MAX_MESSAGE_LENGTH = 8000;

    public record ChatRequest(@NotBlank @Size(max = MAX_MESSAGE_LENGTH) String message) {}

    public record ChatResponse(String answer, UsageService.AnswerUsage usage) {}

    public record ChatToken(String text) {}

    /** The last event of a stream that finished normally: what the answer used. */
    public record ChatUsageEvent(UsageService.AnswerUsage usage) {}

    /** What the chat page needs to render itself: provider and branding. Never includes secrets. */
    public record AppInfo(String appName, String welcomeMessage, String accentColor,
                          String chatProvider, String embeddingProvider) {}

    private final ChatService chatService;
    private final UsageService usage;
    private final StarterKitProperties properties;
    private final String chatProvider;
    private final String embeddingProvider;

    public ChatController(ChatService chatService, UsageService usage, StarterKitProperties properties,
                          @Value("${spring.ai.model.chat:ollama}") String chatProvider,
                          @Value("${spring.ai.model.embedding:ollama}") String embeddingProvider) {
        this.chatService = chatService;
        this.usage = usage;
        this.properties = properties;
        this.chatProvider = chatProvider;
        this.embeddingProvider = embeddingProvider;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        String subject = UsageSubjects.of(http);
        usage.reserve(subject);
        ChatAnswer answer = chatService.chat(request.message());
        return new ChatResponse(answer.text(), usage.record(subject, answer.usage()));
    }

    /**
     * Streams the answer as Server-Sent Events. Each event's data is JSON like {"text":" world"}:
     * plain-text events would lose leading spaces and break on newlines in standard SSE clients.
     *
     * <p>If the provider fails before the first token, the client gets a normal problem response with
     * its status code. After that the 200 has already been sent, so the problem (title, detail, status)
     * arrives as the last event instead of the stream just stopping.
     *
     * <p>A stream that completes ends with a {"usage":{...}} event. Tokens used by a stream that fails
     * or is cancelled are still counted.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> stream(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        String subject = UsageSubjects.of(http);
        usage.reserve(subject); // throws before the stream starts, so the client gets a normal 429
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean recorded = new AtomicBoolean();
        AtomicReference<TokenUsage> tokens = new AtomicReference<>(TokenUsage.NONE);
        return chatService.stream(request.message())
                .doOnNext(chunk -> tokens.accumulateAndGet(chunk.usage(), TokenUsage::max))
                .filter(chunk -> !chunk.text().isEmpty())
                .<Object>map(chunk -> new ChatToken(chunk.text()))
                .doOnNext(token -> started.set(true))
                .concatWith(Mono.fromCallable(() -> {
                    recorded.set(true);
                    return new ChatUsageEvent(usage.record(subject, tokens.get()));
                }))
                .onErrorResume(ex -> started.get()
                        ? Flux.just(ApiExceptionHandler.streamProblem(ex))
                        : Flux.error(ex))
                .doFinally(signal -> {
                    if (recorded.compareAndSet(false, true)) {
                        usage.record(subject, tokens.get());
                    }
                });
    }

    @GetMapping("/info")
    public AppInfo info() {
        var b = properties.getBranding();
        return new AppInfo(b.getAppName(), b.getWelcomeMessage(), b.getAccentColor(), chatProvider, embeddingProvider);
    }
}
