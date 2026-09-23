package fr.cdaacademy.exercise;

import java.util.List;

/**
 * Résultat de la validation d'une réponse.
 * @param feedback remarques précises (ce qui manque, ce qui est faux)
 * @param details  données propres au type d'exercice (résultats SQL comparés, trous corrigés...)
 */
public record ValidationResult(boolean success, String message, List<String> feedback, Object details) {

    public static ValidationResult ok(String message, Object details) {
        return new ValidationResult(true, message, List.of(), details);
    }

    public static ValidationResult ko(String message, List<String> feedback, Object details) {
        return new ValidationResult(false, message, feedback, details);
    }
}
