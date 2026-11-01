package pl.tokstudiow.api.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pl.tokstudiow.api.user.Role;

import java.util.UUID;

/** Dane zalogowanego użytkownika – zawsze z podpisanego tokenu, nigdy z treści żądania. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        return UUID.fromString(token().getName());
    }

    public static Role role() {
        return Role.valueOf(token().getToken().getClaimAsString("role"));
    }

    private static JwtAuthenticationToken token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt;
        }
        throw new IllegalStateException("No authenticated user");
    }
}