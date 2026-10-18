package dev.starterkit.ai.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.starterkit.ai.chat.ChatAnswer;
import dev.starterkit.ai.chat.ChatService;
import dev.starterkit.ai.chat.TokenUsage;
import dev.starterkit.ai.config.StarterKitProperties;
import dev.starterkit.ai.rag.DocumentCatalog;
import dev.starterkit.ai.rag.DocumentService;
import dev.starterkit.ai.usage.UsageService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The real security filter chain with login on, in front of the real controllers. */
@WebMvcTest(properties = "starter-kit.security.enabled=true")
@Import({SecurityConfig.class, AccountUserDetailsService.class, SecurityEnabledTest.Properties.class})
class SecurityEnabledTest {

    @EnableConfigurationProperties(StarterKitProperties.class)
    static class Properties {}

    private static final String CHAT = "{\"message\":\"Hello\"}";

    /**
     * The real browser flow: the login page sets the XSRF-TOKEN cookie, and the page sends its value
     * back in the X-XSRF-TOKEN header. (Spring Security's csrf() test helper is not used on purpose:
     * it swaps the token store inside the shared test context and leaks into later tests.)
     */
    private MockHttpServletRequestBuilder withXsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token = mvc.perform(get("/login.html")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

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

    @BeforeEach
    void users() {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        when(accounts.find(anyString())).thenReturn(Optional.empty());
        when(accounts.find("alice")).thenReturn(Optional.of(new UserAccounts.Stored(
                "alice", encoder.encode("alice password 1"), UserAccounts.Role.USER, true)));
        when(accounts.find("root")).thenReturn(Optional.of(new UserAccounts.Stored(
                "root", encoder.encode("root password 12"), UserAccounts.Role.ADMIN, true)));
        when(accounts.find("gone")).thenReturn(Optional.of(new UserAccounts.Stored(
                "gone", encoder.encode("gone password 12"), UserAccounts.Role.USER, false)));
        when(chatService.chat(anyString())).thenReturn(new ChatAnswer("Hi", TokenUsage.NONE));
        when(usage.record(anyString(), any())).thenReturn(new UsageService.AnswerUsage(0, 0, 0));
    }

    @Test
    void apiWithoutSignInIsA401ProblemThatNeverTriggersTheBrowserLoginBox() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.title").value("Sign-in required"));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(CHAT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pagesWithoutSignInRedirectToTheLoginPage() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void theLoginPageIsPublicAndHandsOutTheCsrfCookie() throws Exception {
        mvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void formLoginAnswersWithStatusCodesForTheFetchBasedLoginPage() throws Exception {
        mvc.perform(withXsrf(post("/login")).param("username", "alice").param("password", "alice password 1"))
                .andExpect(status().isNoContent());
        mvc.perform(withXsrf(post("/login")).param("username", "alice").param("password", "wrong password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong username or password."));
        mvc.perform(withXsrf(post("/login")).param("username", "gone").param("password", "gone password 12"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutAValidCsrfTokenNeverSucceeds() throws Exception {
        // Refused with 401 or 403 depending on which entry point Spring Security picks; never a 204 sign-in
        mvc.perform(post("/login").param("username", "alice").param("password", "alice password 1"))
                .andExpect(status().is4xxClientError())
                .andExpect(cookie().doesNotExist("SESSION"))
                .andExpect(header().doesNotExist("Location"));
        Cookie token = mvc.perform(get("/login.html")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/login").cookie(token).header("X-XSRF-TOKEN", "forged")
                        .param("username", "alice").param("password", "alice password 1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void signedInBrowserRequestsNeedTheCsrfToken() throws Exception {
        mvc.perform(post("/api/chat").with(user("alice")).contentType(MediaType.APPLICATION_JSON).content(CHAT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not allowed"));
        mvc.perform(withXsrf(post("/api/chat")).with(user("alice")).contentType(MediaType.APPLICATION_JSON).content(CHAT))
                .andExpect(status().isOk());
    }

    @Test
    void apiClientsCanUseBasicAuthWithoutACsrfToken() throws Exception {
        mvc.perform(post("/api/chat").with(httpBasic("alice", "alice password 1"))
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hi"));
        verify(usage).reserve("user:alice"); // usage is counted per user, not per IP
        mvc.perform(post("/api/chat").with(httpBasic("alice", "wrong password"))
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void meSaysWhoIsSignedIn() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic("root", "root password 12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled").value(true))
                .andExpect(jsonPath("$.username").value("root"))
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void adminEndpointsNeedTheAdminRole() throws Exception {
        when(accounts.list()).thenReturn(List.of());
        mvc.perform(get("/api/admin/users").with(httpBasic("alice", "alice password 1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/usage").with(httpBasic("alice", "alice password 1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(httpBasic("root", "root password 12")))
                .andExpect(status().isOk());
    }

    @Test
    void adminsCreateUsersAndCannotDeleteThemselves() throws Exception {
        when(accounts.list()).thenReturn(List.of(new UserAccounts.Account("bob", UserAccounts.Role.USER, true, null)));
        mvc.perform(post("/api/admin/users").with(httpBasic("root", "root password 12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"bob password 12\",\"role\":\"USER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(accounts).create("bob", "bob password 12", UserAccounts.Role.USER);

        mvc.perform(delete("/api/admin/users/root").with(httpBasic("root", "root password 12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("own account")));
    }

    @Test
    void healthProbesStayOpenForTheOrchestrator() throws Exception {
        // Actuator itself is not part of this test slice; a 404 (not a 401) shows security let it through
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
    }
}
