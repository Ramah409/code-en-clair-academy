package fr.cdaacademy.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import fr.cdaacademy.common.UnauthorizedException;

/** Accès pratique à l'utilisatrice authentifiée depuis les services. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new UnauthorizedException("Connecte-toi pour accéder à cette ressource.");
    }

    public static Long id() {
        return get().id();
    }
}
