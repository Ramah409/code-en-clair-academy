package fr.cdaacademy.auth.dto;

import fr.cdaacademy.user.dto.PasswordRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail n'est pas valide.")
        @Size(max = 254, message = "L'adresse e-mail est trop longue.")
        String email,

        @NotBlank(message = "Le nom affiché est obligatoire.")
        @Size(min = 2, max = 60, message = "Le nom affiché doit contenir entre 2 et 60 caractères.")
        String displayName,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
        String password,

        @NotBlank(message = "La confirmation du mot de passe est obligatoire.")
        String confirmPassword,

        boolean acceptTerms) {

    @AssertTrue(message = "Les deux mots de passe ne correspondent pas.")
    public boolean isPasswordConfirmed() {
        return password == null || password.equals(confirmPassword);
    }

    @AssertTrue(message = "Tu dois accepter la politique de confidentialité pour créer un compte.")
    public boolean isTermsAccepted() {
        return acceptTerms;
    }
}
