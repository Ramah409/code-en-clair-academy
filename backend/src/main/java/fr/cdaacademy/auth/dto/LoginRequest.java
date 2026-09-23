package fr.cdaacademy.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(max = 200)
        String password) {
}
