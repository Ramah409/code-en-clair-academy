package fr.cdaacademy.user.dto;

import java.time.Instant;

/** Profil de l'utilisatrice connectée (jamais de mot de passe ni de hash). */
public record UserResponse(
        Long id,
        String email,
        String displayName,
        String role,
        int xp,
        int level,
        int levelStartXp,
        int nextLevelXp,
        int currentStreak,
        int longestStreak,
        int dailyGoalMinutes,
        String theme,
        Instant createdAt) {
}
