package fr.cdaacademy.user.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeEnabledRequest(@NotNull(message = "L'état du compte est obligatoire.") Boolean enabled) {
}
