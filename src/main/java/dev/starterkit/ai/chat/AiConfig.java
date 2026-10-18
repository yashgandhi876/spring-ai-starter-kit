package dev.starterkit.ai.chat;

import dev.starterkit.ai.config.StarterKitProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One ChatClient for the whole app. Which model sits behind it (Claude, OpenAI or Ollama)
 * is decided by the spring.ai.model.chat property, so switching providers needs no code change.
 */
@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, StarterKitProperties properties) {
        return builder.defaultSystem(properties.getPrompts().getSystem()).build();
    }
}
