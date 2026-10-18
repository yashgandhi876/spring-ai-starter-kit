package dev.starterkit.ai.security;

import dev.starterkit.ai.config.StarterKitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from ADMIN_USERNAME and ADMIN_PASSWORD when login is on and that user does
 * not exist yet. An existing admin is never changed, so ADMIN_PASSWORD is only needed on first start.
 */
@Component
class AdminBootstrap implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /** Login is on but there is no admin and no usable ADMIN_PASSWORD. */
    static class MissingAdminPasswordException extends RuntimeException {
        MissingAdminPasswordException(String reason) {
            super(reason);
        }
    }

    private final StarterKitProperties.Security security;
    private final UserAccounts accounts;

    AdminBootstrap(StarterKitProperties properties, UserAccounts accounts) {
        this.security = properties.getSecurity();
        this.accounts = accounts;
    }

    @Override
    public void afterPropertiesSet() {
        if (!security.isEnabled() || accounts.exists(security.getAdminUsername())) {
            return;
        }
        try {
            accounts.create(security.getAdminUsername(), security.getAdminPassword(), UserAccounts.Role.ADMIN);
        } catch (UserAccounts.InvalidAccountException e) {
            throw new MissingAdminPasswordException(e.getMessage());
        } catch (UserAccounts.AccountExistsException e) {
            return; // another instance created it at the same moment
        }
        log.info("Created admin user '{}'", security.getAdminUsername());
    }
}
