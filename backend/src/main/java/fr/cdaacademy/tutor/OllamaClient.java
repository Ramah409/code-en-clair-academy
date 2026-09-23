package fr.cdaacademy.tutor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.config.AppProperties;

/**
 * Client minimal de l'API Ollama (modèle exécuté localement : aucune donnée ne sort de la machine).
 * La disponibilité est mise en cache 30 secondes pour ne pas ralentir chaque question.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final Duration STATUS_TTL = Duration.ofSeconds(30);

    public record Message(String role, String content) {
    }

    public static class OllamaUnavailableException extends RuntimeException {
        public OllamaUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    private volatile Boolean available;
    private volatile Instant checkedAt = Instant.EPOCH;

    public OllamaClient(ObjectMapper json, AppProperties props) {
        this.json = json;
        this.baseUrl = props.ollama().baseUrl().replaceAll("/+$", "");
        this.model = props.ollama().model();
        this.timeout = Duration.ofSeconds(Math.max(5, props.ollama().timeoutSeconds()));
    }

    public String model() {
        return model;
    }

    /** Ollama répond-il, et le modèle configuré est-il installé ? */
    public boolean available() {
        if (available != null && Instant.now().isBefore(checkedAt.plus(STATUS_TTL))) {
            return available;
        }
        boolean ok;
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            ok = false;
            if (res.statusCode() == 200) {
                for (JsonNode m : json.readTree(res.body()).path("models")) {
                    String name = m.path("name").asText();
                    ok |= name.equals(model) || name.equals(model + ":latest");
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            ok = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ok = false;
        }
        available = ok;
        checkedAt = Instant.now();
        return ok;
    }

    /** Conversation non diffusée en flux : renvoie la réponse complète du modèle. */
    public String chat(List<Message> messages) {
        try {
            String body = json.writeValueAsString(Map.of("model", model, "messages", messages, "stream", false,
                    "options", Map.of("temperature", 0.3, "num_predict", 700)));
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new OllamaUnavailableException("Ollama a répondu " + res.statusCode(), null);
            }
            String content = json.readTree(res.body()).path("message").path("content").asText("").strip();
            if (content.isEmpty()) {
                throw new OllamaUnavailableException("Réponse vide d'Ollama", null);
            }
            return content;
        } catch (IOException e) {
            available = false;
            checkedAt = Instant.now();
            log.warn("Ollama indisponible : {}", e.getMessage());
            throw new OllamaUnavailableException("Ollama indisponible", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OllamaUnavailableException("Requête interrompue", e);
        }
    }
}
