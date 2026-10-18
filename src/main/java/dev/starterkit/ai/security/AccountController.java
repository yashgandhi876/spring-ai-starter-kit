package dev.starterkit.ai.security;

import dev.starterkit.ai.config.StarterKitProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Who is signed in. Always available, so the chat page knows whether to show a sign-out button. */
@RestController
@RequestMapping("/api")
public class AccountController {

    public record Me(boolean authEnabled, String username, boolean admin) {}

    private final boolean authEnabled;

    public AccountController(StarterKitProperties properties) {
        this.authEnabled = properties.getSecurity().isEnabled();
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        if (!authEnabled || auth == null) {
            return new Me(authEnabled, null, false);
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new Me(true, auth.getName(), admin);
    }

    /** Changing your own password. Only exists when login is on. */
    @RestController
    @RequestMapping("/api/me")
    @ConditionalOnProperty(name = "starter-kit.security.enabled", havingValue = "true")
    static class Password {

        public record ChangePassword(@NotBlank String currentPassword, @NotBlank String newPassword) {}

        private final UserAccounts accounts;

        Password(UserAccounts accounts) {
            this.accounts = accounts;
        }

        @PostMapping("/password")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void change(@Valid @RequestBody ChangePassword request, Principal user) {
            if (!accounts.changePassword(user.getName(), request.currentPassword(), request.newPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current password is wrong.");
            }
        }
    }

    /** User management for admins. Only exists when login is on; /api/admin/** needs the ADMIN role. */
    @RestController
    @RequestMapping("/api/admin/users")
    @ConditionalOnProperty(name = "starter-kit.security.enabled", havingValue = "true")
    static class Users {

        public record NewUser(@NotBlank String username, @NotBlank String password, @NotNull UserAccounts.Role role) {}

        private final UserAccounts accounts;

        Users(UserAccounts accounts) {
            this.accounts = accounts;
        }

        @GetMapping
        public List<UserAccounts.Account> list() {
            return accounts.list();
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public UserAccounts.Account create(@Valid @RequestBody NewUser request) {
            accounts.create(request.username(), request.password(), request.role());
            return accounts.list().stream().filter(a -> a.username().equals(request.username())).findFirst().orElseThrow();
        }

        @DeleteMapping("/{username}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(@PathVariable String username, Principal admin) {
            if (username.equals(admin.getName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't delete your own account.");
            }
            if (!accounts.delete(username)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No user named " + username + ".");
            }
        }
    }
}
