package dev.starterkit.ai.security;

import dev.starterkit.ai.config.StarterKitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Login is off by default (AUTH_ENABLED=false), so the app behaves like v0.1. When it is on:
 * <ul>
 *   <li>the chat page signs in with a form on /login.html and a session cookie, protected against
 *       CSRF with the XSRF-TOKEN cookie and X-XSRF-TOKEN header;</li>
 *   <li>API clients can use HTTP Basic (curl -u user:password);</li>
 *   <li>/api/admin/** needs the ADMIN role, health probes stay open.</li>
 * </ul>
 * API errors are RFC 9457 problems. A 401 never sends WWW-Authenticate, so browsers never show their
 * own Basic login box and never cache Basic credentials. That is also why requests carrying a Basic
 * Authorization header can skip the CSRF check: a cross-site page cannot make the browser add one.
 */
@Configuration
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, StarterKitProperties properties) throws Exception {
        if (!properties.getSecurity().isEnabled()) {
            return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable)
                    .build();
        }
        RequestMatcher api = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
        RequestMatcher basicAuth = request -> {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            return header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
        };
        AuthenticationEntryPoint apiUnauthorized = (request, response, ex) ->
                writeProblem(response, 401, "Sign-in required", "Sign in, or send HTTP Basic credentials.");
        // A missing CSRF token is also an "access denied"; for a caller who isn't signed in at all,
        // "sign in" is the useful answer
        AccessDeniedHandler apiForbidden = (request, response, ex) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                apiUnauthorized.commence(request, response, null);
            } else {
                writeProblem(response, 403, "Not allowed", "Your account can't do this, or the CSRF token is missing.");
            }
        };

        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login.html", "/error", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // The login page posts with fetch, so answer with status codes instead of redirects
                .formLogin(f -> f
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, auth) -> response.setStatus(204))
                        .failureHandler((request, response, ex) ->
                                writeProblem(response, 401, "Sign-in failed", "Wrong username or password."))
                        .permitAll())
                .httpBasic(b -> b.authenticationEntryPoint(apiUnauthorized))
                .logout(l -> l
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .csrf(c -> c.spa().ignoringRequestMatchers(basicAuth))
                // Make sure the XSRF-TOKEN cookie is sent even before the page has made a POST
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(apiUnauthorized, api)
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login.html"),
                                AnyRequestMatcher.INSTANCE)
                        .defaultAccessDeniedHandlerFor(apiForbidden, api))
                .build();
    }

    /** Loading the deferred CSRF token is what makes Spring Security write the cookie. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    /** Writes a fixed RFC 9457 problem. Titles and details are constants, so no escaping is needed. */
    static void writeProblem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(title, status, detail));
    }
}
