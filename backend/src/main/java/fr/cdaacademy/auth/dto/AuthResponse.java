package fr.cdaacademy.auth.dto;

import java.time.Instant;

import fr.cdaacademy.user.dto.UserResponse;

/** Réponse d'authentification. Le jeton de rafraîchissement voyage uniquement dans un cookie HttpOnly. */
public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
}
