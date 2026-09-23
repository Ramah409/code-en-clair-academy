package fr.cdaacademy.common;

import java.time.Instant;
import java.util.Map;

/**
 * Format unique des réponses d'erreur de l'API.
 * Le message est toujours rédigé en français et ne contient aucun détail technique interne.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }
}
