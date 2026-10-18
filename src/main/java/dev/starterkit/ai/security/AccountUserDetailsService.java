package dev.starterkit.ai.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/** Lets Spring Security sign people in against the app_user table. */
@Component
class AccountUserDetailsService implements UserDetailsService {

    private final UserAccounts accounts;

    AccountUserDetailsService(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccounts.Stored user = accounts.find(username)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .roles(user.role().name())
                .disabled(!user.enabled())
                .build();
    }
}
