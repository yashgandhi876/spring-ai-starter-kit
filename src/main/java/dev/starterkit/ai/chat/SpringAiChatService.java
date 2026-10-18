package dev.starterkit.ai.chat;

import dev.starterkit.ai.config.StarterKitProperties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
class SpringAiChatService implements ChatService {

    private final ChatClient chatClient;
    private final StarterKitProperties properties;

    SpringAiChatService(ChatClient chatClient, StarterKitProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    @Override
    public ChatAnswer chat(String message) {
        return answer(chatClient.prompt().user(message).call().chatResponse());
    }

    @Override
    public Flux<ChatChunk> stream(String message) {
        return chatClient.prompt().user(message).stream().chatResponse()
                .map(r -> new ChatChunk(text(r), r.getMetadata() == null ? TokenUsage.NONE : TokenUsage.from(r.getMetadata().getUsage())))
                // Usage-only chunks carry no text; keep them so the controller sees the final count
                .filter(c -> !c.text().isEmpty() || c.usage().total() > 0);
    }

    @Override
    public ChatAnswer answerWithContext(String question, String context) {
        return answer(chatClient.prompt().user(buildRagPrompt(properties.getPrompts().getRag(), question, context))
                .call().chatResponse());
    }

    private static ChatAnswer answer(ChatResponse response) {
        if (response == null) {
            return new ChatAnswer("", TokenUsage.NONE);
        }
        return new ChatAnswer(text(response),
                response.getMetadata() == null ? TokenUsage.NONE : TokenUsage.from(response.getMetadata().getUsage()));
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(context|question)}");

    /**
     * Fills {context} and {question} in one pass, so placeholder-like text inside a document
     * or a question is never substituted a second time. No template engine on purpose:
     * user text may contain braces that one would choke on.
     */
    static String buildRagPrompt(String template, String question, String context) {
        return PLACEHOLDER.matcher(template).replaceAll(m ->
                Matcher.quoteReplacement("context".equals(m.group(1)) ? context : question));
    }
}
