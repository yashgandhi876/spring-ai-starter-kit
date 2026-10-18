package dev.starterkit.ai.chat;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import java.time.Duration;
import dev.starterkit.ai.usage.UsageService;
import dev.starterkit.ai.usage.UsageLimitExceededException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.starterkit.ai.config.StarterKitProperties;
import dev.starterkit.ai.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Flux;

class ChatControllerTest {

    private ChatService chatService;
    private UsageService usage;

    private static Flux<ChatChunk> chunks(String... texts) {
        return Flux.fromArray(texts).map(t -> new ChatChunk(t, TokenUsage.NONE));
    }
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        usage = mock(UsageService.class);
        when(usage.record(anyString(), any())).thenAnswer(call -> {
            TokenUsage tokens = call.getArgument(1);
            return new UsageService.AnswerUsage(tokens.inputTokens(), tokens.outputTokens(), 0.0);
        });
        StarterKitProperties properties = new StarterKitProperties();
        properties.getBranding().setAppName("Acme Assistant");
        properties.getBranding().setAccentColor("#123abc");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService, usage, properties, "anthropic", "openai"))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsAnswer() throws Exception {
        when(chatService.chat("Hello")).thenReturn(new ChatAnswer("Hi there", new TokenUsage(7, 3)));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hi there"))
                .andExpect(jsonPath("$.usage.inputTokens").value(7))
                .andExpect(jsonPath("$.usage.outputTokens").value(3));
        verify(usage).reserve("ip:127.0.0.1");
        verify(usage).record("ip:127.0.0.1", new TokenUsage(7, 3));
    }

    @Test
    void rejectsBlankMessageWithProblemDetail() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value(containsString("message")));
        verifyNoInteractions(chatService);
    }

    @Test
    void rejectsMessageOverTheLengthLimit() throws Exception {
        String tooLong = "a".repeat(ChatController.MAX_MESSAGE_LENGTH + 1);

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsProviderFailureAsBadGatewayWithoutLeakingDetails() throws Exception {
        when(chatService.chat(anyString()))
                .thenThrow(new NonTransientAiException("401 invalid x-api-key sk-secret"));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("AI request failed"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("sk-secret"))));
    }

    @Test
    void reportsUnknownFailureAsServerErrorWithoutLeakingDetails() throws Exception {
        when(chatService.chat(anyString())).thenThrow(new IllegalStateException("internal detail xyz"));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Unexpected error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("xyz"))));
    }

    @Test
    void streamsTokensAsServerSentEvents() throws Exception {
        when(chatService.stream("Hello")).thenReturn(Flux.concat(chunks("Hi", " there", "\nnext line"),
                // the provider's usage arrives on a chunk without text, at the end
                Flux.just(new ChatChunk("", new TokenUsage(12, 4)))));

        MvcResult started = mvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("data:{\"text\":\"Hi\"}")))
                // leading spaces and newlines survive inside the JSON payload
                .andExpect(content().string(containsString("data:{\"text\":\" there\"}")))
                .andExpect(content().string(containsString("data:{\"text\":\"\\nnext line\"}")))
                .andExpect(content().string(containsString("data:{\"usage\":{\"inputTokens\":12,\"outputTokens\":4,\"costUsd\":0.0}}")))
                // the usage-only chunk is not sent as an empty text event
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data:{\"text\":\"\"}"))));
        verify(usage).reserve("ip:127.0.0.1");
        verify(usage, times(1)).record("ip:127.0.0.1", new TokenUsage(12, 4));
    }

    @Test
    void reportsBusyProviderAsServiceUnavailable() throws Exception {
        when(chatService.chat(anyString())).thenThrow(new TransientAiException("529 overloaded_error"));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI provider busy"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("overloaded_error"))));
    }

    @Test
    void streamFailingBeforeTheFirstTokenGetsANormalProblemResponse() throws Exception {
        when(chatService.stream("Hello")).thenReturn(Flux.error(new TransientAiException("529 overloaded_error")));

        MvcResult started = mvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("AI provider busy")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("overloaded_error"))));
    }

    @Test
    void streamFailingMidwaySendsTheProblemAsTheLastEvent() throws Exception {
        when(chatService.stream("Hello")).thenReturn(Flux.concat(
                chunks("Partial", " answer"),
                Flux.just(new ChatChunk("", new TokenUsage(9, 2))),
                Flux.error(new TransientAiException("529 overloaded_error"))));

        MvcResult started = mvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("data:{\"text\":\"Partial\"}", "data:{\"text\":\" answer\"}");
        // It must be a real SSE event: SSE clients drop a problem appended as bare JSON
        String[] events = body.strip().split("\n\n");
        String last = events[events.length - 1];
        assertThat(last).startsWith("data:{").contains("\"title\":\"AI provider busy\"", "\"status\":503", "try again")
                .doesNotContain("overloaded_error");
    }

    @Test
    void tokensUsedBeforeAMidStreamFailureAreStillCounted() throws Exception {
        when(chatService.stream("Hello")).thenReturn(Flux.concat(
                Flux.just(new ChatChunk("Partial", new TokenUsage(9, 2))),
                Flux.error(new TransientAiException("529 overloaded_error"))));

        MvcResult started = mvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM).content("{\"message\":\"Hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        verify(usage, times(1)).record("ip:127.0.0.1", new TokenUsage(9, 2));
    }

    @Test
    void chatAndStreamOverTheDailyLimitAreRefusedBeforeCallingTheModel() throws Exception {
        doThrow(new UsageLimitExceededException("You have used all 3 AI requests for today.", Duration.ofSeconds(42)))
                .when(usage).reserve(anyString());

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.title").value("Daily limit reached"));
        mvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(chatService);
    }

    @Test
    void infoShowsBrandingAndProvidersButNoSecrets() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("Acme Assistant"))
                .andExpect(jsonPath("$.accentColor").value("#123abc"))
                .andExpect(jsonPath("$.chatProvider").value("anthropic"))
                .andExpect(jsonPath("$.embeddingProvider").value("openai"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("apiKey"))));
    }
}
