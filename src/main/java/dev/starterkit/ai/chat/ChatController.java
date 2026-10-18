package dev.starterkit.ai.chat;

import dev.starterkit.ai.config.StarterKitProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    static final int MAX_MESSAGE_LENGTH = 8000;

    public record ChatRequest(@NotBlank @Size(max = MAX_MESSAGE_LENGTH) String message) {}

    public record ChatResponse(String answer) {}

    public record ChatToken(String text) {}

    /** What the chat page needs to render itself: provider and branding. Never includes secrets. */
    public record AppInfo(String appName, String welcomeMessage, String accentColor,
                          String chatProvider, String embeddingProvider) {}

    private final ChatService chatService;
    private final StarterKitProperties properties;
    private final String chatProvider;
    private final String embeddingProvider;

    public ChatController(ChatService chatService, StarterKitProperties properties,
                          @Value("${spring.ai.model.chat:ollama}") String chatProvider,
                          @Value("${spring.ai.model.embedding:ollama}") String embeddingProvider) {
        this.chatService = chatService;
        this.properties = properties;
        this.chatProvider = chatProvider;
        this.embeddingProvider = embeddingProvider;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chat(request.message()));
    }

    /**
     * Streams the answer as Server-Sent Events. Each event's data is JSON like {"text":" world"}:
     * plain-text events would lose leading spaces and break on newlines in standard SSE clients.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatToken> stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request.message()).map(ChatToken::new);
    }

    @GetMapping("/info")
    public AppInfo info() {
        var b = properties.getBranding();
        return new AppInfo(b.getAppName(), b.getWelcomeMessage(), b.getAccentColor(), chatProvider, embeddingProvider);
    }
}
