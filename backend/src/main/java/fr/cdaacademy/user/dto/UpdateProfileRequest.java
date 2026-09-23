package fr.cdaacademy.user.dto;

import fr.cdaacademy.user.Theme;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Le nom affiché est obligatoire.")
        @Size(min = 2, max = 60, message = "Le nom affiché doit contenir entre 2 et 60 caractères.")
        String displayName,

        @NotNull(message = "L'objectif quotidien est obligatoire.")
        @Min(value = 5, message = "L'objectif quotidien doit être d'au moins 5 minutes.")
        @Max(value = 240, message = "L'objectif quotidien ne peut pas dépasser 240 minutes.")
        Integer dailyGoalMinutes,

        @NotNull(message = "Le thème est obligatoire.")
        Theme theme) {
}
