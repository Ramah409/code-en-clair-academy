package fr.cdaacademy.user;

import fr.cdaacademy.user.dto.AdminUserResponse;
import fr.cdaacademy.user.dto.UserResponse;

/** Conversion entité → DTO. Le hash du mot de passe n'est jamais exposé. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User u) {
        int level = LevelCalculator.level(u.getXp());
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().getCode().name(),
                u.getXp(), level, LevelCalculator.xpForLevel(level), LevelCalculator.xpForLevel(level + 1),
                u.getCurrentStreak(), u.getLongestStreak(), u.getDailyGoalMinutes(), u.getTheme().name(),
                u.getCreatedAt());
    }

    public static AdminUserResponse toAdminResponse(User u) {
        return new AdminUserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().getCode().name(),
                u.isEnabled(), u.getXp(), LevelCalculator.level(u.getXp()), u.getCreatedAt());
    }
}
