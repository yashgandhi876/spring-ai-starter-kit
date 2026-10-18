package dev.starterkit.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringAiChatServiceTest {

    @Test
    void fillsContextAndQuestion() {
        String prompt = SpringAiChatService.buildRagPrompt("C={context} Q={question}", "Why?", "Because.");

        assertThat(prompt).isEqualTo("C=Because. Q=Why?");
    }

    @Test
    void doesNotSubstitutePlaceholdersFoundInsideUserText() {
        String prompt = SpringAiChatService.buildRagPrompt(
                "C={context} Q={question}", "what is {context}?", "doc mentions {question}");

        assertThat(prompt).isEqualTo("C=doc mentions {question} Q=what is {context}?");
    }

    @Test
    void keepsDollarSignsAndBackslashesInUserText() {
        String prompt = SpringAiChatService.buildRagPrompt("{context}|{question}", "cost $5\\month?", "price is $1");

        assertThat(prompt).isEqualTo("price is $1|cost $5\\month?");
    }
}
