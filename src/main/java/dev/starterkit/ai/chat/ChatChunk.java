package dev.starterkit.ai.chat;

/** One piece of a streamed answer. usage is the provider's count so far, or TokenUsage.NONE. */
public record ChatChunk(String text, TokenUsage usage) {}
