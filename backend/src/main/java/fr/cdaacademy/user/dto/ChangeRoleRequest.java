package fr.cdaacademy.user.dto;

import fr.cdaacademy.user.RoleCode;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Le rôle est obligatoire.") RoleCode role) {
}
