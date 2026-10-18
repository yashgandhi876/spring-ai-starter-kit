package dev.starterkit.ai.chat;

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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Flux;

class ChatControllerTest {

    private ChatService chatService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        StarterKitProperties properties = new StarterKitProperties();
        properties.getBranding().setAppName("Acme Assistant");
        properties.getBranding().setAccentColor("#123abc");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService, properties, "anthropic", "openai"))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsAnswer() throws Exception {
        when(chatService.chat("Hello")).thenReturn("Hi there");

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hi there"));
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
        when(chatService.stream("Hello")).thenReturn(Flux.just("Hi", " there", "\nnext line"));

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
                .andExpect(content().string(containsString("data:{\"text\":\"\\nnext line\"}")));
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
