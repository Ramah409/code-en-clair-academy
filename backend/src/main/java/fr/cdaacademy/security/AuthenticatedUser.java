package fr.cdaacademy.security;

import fr.cdaacademy.user.RoleCode;

/** Identité de l'utilisatrice authentifiée, placée dans le SecurityContext. */
public record AuthenticatedUser(Long id, String email, RoleCode role) {
}
