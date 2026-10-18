package dev.starterkit.ai.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.starterkit.ai.config.StarterKitProperties;
import dev.starterkit.ai.testsupport.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class AdminBootstrapTest {

    private final UserAccounts accounts = new UserAccounts(new JdbcTemplate(TestDatabase.dataSource()),
            PasswordEncoderFactories.createDelegatingPasswordEncoder());
    private StarterKitProperties properties;

    @BeforeEach
    void clean() {
        TestDatabase.truncate("app_user");
        properties = new StarterKitProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setAdminUsername("root");
    }

    @Test
    void createsTheFirstAdmin() {
        properties.getSecurity().setAdminPassword("a long admin password");

        new AdminBootstrap(properties, accounts).afterPropertiesSet();

        assertThat(accounts.list()).singleElement()
                .satisfies(a -> assertThat(a.role()).isEqualTo(UserAccounts.Role.ADMIN));
    }

    @Test
    void neverChangesAnExistingAdmin() {
        accounts.create("root", "the original password", UserAccounts.Role.ADMIN);
        properties.getSecurity().setAdminPassword(""); // not needed once the admin exists

        new AdminBootstrap(properties, accounts).afterPropertiesSet();

        assertThat(accounts.changePassword("root", "the original password", "the original password")).isTrue();
    }

    @Test
    void stopsStartupWithAClearMessageWhenThePasswordIsMissing() {
        AdminBootstrap bootstrap = new AdminBootstrap(properties, accounts);

        assertThatThrownBy(bootstrap::afterPropertiesSet)
                .isInstanceOf(AdminBootstrap.MissingAdminPasswordException.class)
                .satisfies(e -> {
                    FailureAnalysis analysis = new MissingAdminPasswordFailureAnalyzer()
                            .analyze(new IllegalStateException("bean failed", e));
                    assertThat(analysis.getDescription()).contains("AUTH_ENABLED=true", "12 to 64 characters");
                    assertThat(analysis.getAction()).contains("ADMIN_PASSWORD", "AUTH_ENABLED=false");
                });
        assertThat(accounts.list()).isEmpty();
    }

    @Test
    void doesNothingWhenLoginIsOff() {
        properties.getSecurity().setEnabled(false);

        new AdminBootstrap(properties, accounts).afterPropertiesSet();

        assertThat(accounts.list()).isEmpty();
    }
}
