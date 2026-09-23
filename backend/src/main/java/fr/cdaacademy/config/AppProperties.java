package fr.cdaacademy.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres applicatifs lus depuis application.yml (préfixe "app").
 * Les valeurs sensibles proviennent exclusivement des variables d'environnement.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Content content, Ollama ollama) {

    public record Jwt(String secret, long accessMinutes, long refreshDays, boolean cookieSecure) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Content(String directory) {
    }

    public record Ollama(String baseUrl, String model) {
    }
}
