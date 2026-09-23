package fr.cdaacademy.user.dto;

import jakarta.validation.constraints.NotBlank;

/** La suppression du compte exige de confirmer son mot de passe. */
public record DeleteAccountRequest(
        @NotBlank(message = "Confirme ton mot de passe pour supprimer ton compte.")
        String password) {
}
