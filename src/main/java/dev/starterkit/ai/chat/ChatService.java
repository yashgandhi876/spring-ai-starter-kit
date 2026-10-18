package dev.starterkit.ai.chat;

import reactor.core.publisher.Flux;

/** Talks to whichever chat model is configured. Kept as an interface so it is easy to test and replace. */
public interface ChatService {

    /** Returns the full answer in one piece. */
    String chat(String message);

    /** Streams the answer token by token. */
    Flux<String> stream(String message);

    /** Answers a question using only the given context, for RAG. */
    String answerWithContext(String question, String context);
}
