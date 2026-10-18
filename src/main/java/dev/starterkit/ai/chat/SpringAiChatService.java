package dev.starterkit.ai.chat;

import dev.starterkit.ai.config.StarterKitProperties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
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
    public String chat(String message) {
        return chatClient.prompt().user(message).call().content();
    }

    @Override
    public Flux<String> stream(String message) {
        return chatClient.prompt().user(message).stream().content();
    }

    @Override
    public String answerWithContext(String question, String context) {
        return chatClient.prompt().user(buildRagPrompt(properties.getPrompts().getRag(), question, context))
                .call().content();
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
