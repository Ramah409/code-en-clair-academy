package fr.cdaacademy.user.dto;

import java.time.Instant;

/** Ligne de la liste des comptes côté administration. */
public record AdminUserResponse(
        Long id,
        String email,
        String displayName,
        String role,
        boolean enabled,
        int xp,
        int level,
        Instant createdAt) {
}
