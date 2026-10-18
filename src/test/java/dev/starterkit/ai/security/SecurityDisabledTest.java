package dev.starterkit.ai.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.starterkit.ai.chat.ChatAnswer;
import dev.starterkit.ai.chat.ChatService;
import dev.starterkit.ai.chat.TokenUsage;
import dev.starterkit.ai.config.StarterKitProperties;
import dev.starterkit.ai.rag.DocumentCatalog;
import dev.starterkit.ai.rag.DocumentService;
import dev.starterkit.ai.usage.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** With login off (the default) the app is open like v0.1, and the admin API does not exist. */
@WebMvcTest
@Import({SecurityConfig.class, AccountUserDetailsService.class, SecurityDisabledTest.Properties.class})
class SecurityDisabledTest {

    @EnableConfigurationProperties(StarterKitProperties.class)
    static class Properties {}

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChatService chatService;
    @MockitoBean
    UsageService usage;
    @MockitoBean
    UserAccounts accounts;
    @MockitoBean
    DocumentService documentService;
    @MockitoBean
    DocumentCatalog catalog;

    @Test
    void chatWorksWithoutSignInOrCsrfTokenAndIsCountedPerIp() throws Exception {
        when(chatService.chat(anyString())).thenReturn(new ChatAnswer("Hi", TokenUsage.NONE));
        when(usage.record(anyString(), any())).thenReturn(new UsageService.AnswerUsage(0, 0, 0));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk());
        verify(usage).reserve("ip:127.0.0.1");
    }

    @Test
    void meSaysLoginIsOff() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled").value(false))
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void adminApiDoesNotExistWithoutLogin() throws Exception {
        // Without login the usage report would show every visitor's IP address to anyone
        mvc.perform(get("/api/admin/usage")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/users")).andExpect(status().isNotFound());
        mvc.perform(post("/api/me/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
