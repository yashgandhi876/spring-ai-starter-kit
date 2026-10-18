package dev.starterkit.ai.security;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** The people who can sign in, stored in the app_user table with hashed passwords. */
@Service
public class UserAccounts {

    public enum Role { USER, ADMIN }

    /** A user as shown to admins. Never includes the password hash. */
    public record Account(String username, Role role, boolean enabled, OffsetDateTime createdAt) {}

    record Stored(String username, String passwordHash, Role role, boolean enabled) {}

    /** A username or password that can't be used; the message says why and is safe to show. */
    public static class InvalidAccountException extends RuntimeException {
        public InvalidAccountException(String message) {
            super(message);
        }
    }

    /** The username is already taken. */
    public static class AccountExistsException extends RuntimeException {
        public AccountExistsException(String username) {
            super("A user named " + username + " already exists.");
        }
    }

    static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");
    static final int MIN_PASSWORD = 12;
    static final int MAX_PASSWORD = 64;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public UserAccounts(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public void create(String username, String password, Role role) {
        checkUsername(username);
        checkPassword(password);
        try {
            jdbc.update("INSERT INTO app_user (username, password_hash, role) VALUES (?, ?, ?)",
                    username, encoder.encode(password), role.name());
        } catch (DuplicateKeyException e) {
            throw new AccountExistsException(username);
        }
    }

    public List<Account> list() {
        return jdbc.query("SELECT username, role, enabled, created_at FROM app_user ORDER BY username",
                (rs, i) -> new Account(rs.getString(1), Role.valueOf(rs.getString(2)), rs.getBoolean(3),
                        rs.getObject(4, OffsetDateTime.class)));
    }

    public boolean exists(String username) {
        return find(username).isPresent();
    }

    /** Returns false when there was no such user. */
    public boolean delete(String username) {
        return jdbc.update("DELETE FROM app_user WHERE username = ?", username) > 0;
    }

    /** Changes a password after checking the current one. Returns false when the current one is wrong. */
    public boolean changePassword(String username, String currentPassword, String newPassword) {
        Optional<Stored> user = find(username);
        if (user.isEmpty() || !encoder.matches(currentPassword, user.get().passwordHash())) {
            return false;
        }
        checkPassword(newPassword);
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE username = ?", encoder.encode(newPassword), username);
        return true;
    }

    Optional<Stored> find(String username) {
        return jdbc.query("SELECT username, password_hash, role, enabled FROM app_user WHERE username = ?",
                (rs, i) -> new Stored(rs.getString(1), rs.getString(2), Role.valueOf(rs.getString(3)), rs.getBoolean(4)),
                username).stream().findFirst();
    }

    static void checkUsername(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new InvalidAccountException("Username must be 3 to 50 letters, digits, dots, dashes or underscores.");
        }
    }

    static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new InvalidAccountException(
                    "Password must be %d to %d characters long.".formatted(MIN_PASSWORD, MAX_PASSWORD));
        }
        // BCrypt only looks at the first 72 bytes; refuse longer ones instead of silently ignoring the rest
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAccountException("Password is too long once encoded; use fewer non-ASCII characters.");
        }
    }
}
