package dev.starterkit.ai.security;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Explains what to set when login is on and the first admin can't be created. */
public class MissingAdminPasswordFailureAnalyzer
        extends AbstractFailureAnalyzer<AdminBootstrap.MissingAdminPasswordException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, AdminBootstrap.MissingAdminPasswordException cause) {
        return new FailureAnalysis(
                "Login is on (AUTH_ENABLED=true) but the first admin can't be created: " + cause.getMessage(),
                """
                Set ADMIN_PASSWORD to a password of 12 to 64 characters (and optionally ADMIN_USERNAME, default admin).
                It is only used to create that user on first start; change it later with POST /api/me/password.
                To run without login, set AUTH_ENABLED=false.""",
                cause);
    }
}
