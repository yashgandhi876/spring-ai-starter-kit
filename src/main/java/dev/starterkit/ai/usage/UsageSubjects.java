package dev.starterkit.ai.usage;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

/**
 * Who a request's usage is counted against: the signed-in user, or the client address when
 * login is off. Behind a reverse proxy, set server.forward-headers-strategy=native (or framework)
 * so the real client address is used instead of the proxy's.
 */
public final class UsageSubjects {

    private UsageSubjects() {}

    public static String of(HttpServletRequest request) {
        Principal user = request.getUserPrincipal();
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return "user:" + user.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
