package dev.starterkit.ai.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.starterkit.ai.testsupport.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAccountsTest {

    private final JdbcTemplate jdbc = new JdbcTemplate(TestDatabase.dataSource());
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final UserAccounts accounts = new UserAccounts(jdbc, encoder);

    @BeforeEach
    void clean() {
        TestDatabase.truncate("app_user");
    }

    @Test
    void storesOnlyAHashOfThePassword() {
        accounts.create("alice", "correct horse battery", UserAccounts.Role.USER);

        String stored = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = 'alice'", String.class);
        assertThat(stored).startsWith("{bcrypt}").doesNotContain("correct horse");
        assertThat(encoder.matches("correct horse battery", stored)).isTrue();
        assertThat(accounts.list()).singleElement().satisfies(a -> {
            assertThat(a.username()).isEqualTo("alice");
            assertThat(a.role()).isEqualTo(UserAccounts.Role.USER);
            assertThat(a.enabled()).isTrue();
            assertThat(a.createdAt()).isNotNull();
        });
    }

    @Test
    void refusesDuplicateUsernames() {
        accounts.create("alice", "correct horse battery", UserAccounts.Role.USER);

        assertThatThrownBy(() -> accounts.create("alice", "another long password", UserAccounts.Role.ADMIN))
                .isInstanceOf(UserAccounts.AccountExistsException.class)
                .hasMessage("A user named alice already exists.");
    }

    @Test
    void validatesUsernamesAndPasswords() {
        assertThatThrownBy(() -> accounts.create("al", "correct horse battery", UserAccounts.Role.USER))
                .isInstanceOf(UserAccounts.InvalidAccountException.class);
        assertThatThrownBy(() -> accounts.create("bob smith", "correct horse battery", UserAccounts.Role.USER))
                .isInstanceOf(UserAccounts.InvalidAccountException.class);
        assertThatThrownBy(() -> accounts.create("bob", "short", UserAccounts.Role.USER))
                .isInstanceOf(UserAccounts.InvalidAccountException.class)
                .hasMessage("Password must be 12 to 64 characters long.");
        assertThatThrownBy(() -> accounts.create("bob", "x".repeat(65), UserAccounts.Role.USER))
                .isInstanceOf(UserAccounts.InvalidAccountException.class);
        // 30 characters but 90 bytes: BCrypt would silently ignore everything after byte 72
        assertThatThrownBy(() -> accounts.create("bob", "€".repeat(30), UserAccounts.Role.USER))
                .isInstanceOf(UserAccounts.InvalidAccountException.class)
                .hasMessageContaining("too long once encoded");
        assertThat(accounts.list()).isEmpty();
    }

    @Test
    void changesAPasswordOnlyWithTheCurrentOne() {
        accounts.create("alice", "correct horse battery", UserAccounts.Role.USER);

        assertThat(accounts.changePassword("alice", "wrong password!!", "brand new password")).isFalse();
        assertThat(accounts.changePassword("alice", "correct horse battery", "brand new password")).isTrue();

        String stored = accounts.find("alice").orElseThrow().passwordHash();
        assertThat(encoder.matches("brand new password", stored)).isTrue();
        assertThat(accounts.changePassword("nobody", "x", "brand new password")).isFalse();
    }

    @Test
    void deletesUsers() {
        accounts.create("alice", "correct horse battery", UserAccounts.Role.USER);

        assertThat(accounts.delete("alice")).isTrue();
        assertThat(accounts.delete("alice")).isFalse();
        assertThat(accounts.exists("alice")).isFalse();
    }
}
