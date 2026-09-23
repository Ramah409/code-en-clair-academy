package fr.cdaacademy.user.dto;

/** Politique de mot de passe partagée par l'inscription et le changement de mot de passe. */
public final class PasswordRules {

    /** 10 à 72 caractères (limite de BCrypt), au moins une lettre et un chiffre. */
    public static final String PATTERN = "^(?=.*\\p{L})(?=.*\\d).{10,72}$";

    public static final String MESSAGE =
            "Le mot de passe doit contenir entre 10 et 72 caractères, dont au moins une lettre et un chiffre.";

    private PasswordRules() {
    }
}
