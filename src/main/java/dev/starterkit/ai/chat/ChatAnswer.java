package dev.starterkit.ai.chat;

/** A complete answer and the tokens it took. */
public record ChatAnswer(String text, TokenUsage usage) {}
