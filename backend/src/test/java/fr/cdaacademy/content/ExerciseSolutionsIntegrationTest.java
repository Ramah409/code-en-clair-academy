package fr.cdaacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import fr.cdaacademy.PostgresIntegrationTest;
import fr.cdaacademy.exercise.ExerciseService;

/**
 * Contrôle qualité des contenus : la correction de référence de chaque exercice doit être acceptée
 * par le correcteur automatique, et le code de départ d'un exercice « corriger l'erreur » refusé.
 * Un exercice impossible à valider bloquerait l'apprenante dans sa leçon.
 */
class ExerciseSolutionsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ExerciseService exercises;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    @Test
    void chaqueCorrectionDeReferenceEstAcceptee() throws Exception {
        long userId = jdbc.queryForObject("select id from users where email = 'formatrice@cda-academy.local'",
                Long.class);
        List<String> failures = new ArrayList<>();
        var rows = jdbc.queryForList("select slug, kind, solution, payload::text as payload from exercises");
        assertThat(rows).isNotEmpty();
        for (var row : rows) {
            String slug = (String) row.get("slug");
            String kind = (String) row.get("kind");
            JsonNode payload = json.readTree((String) row.get("payload"));
            JsonNode answer = switch (kind) {
                case "COMPLETER" -> {
                    ArrayNode blanks = json.createArrayNode();
                    payload.path("blanks").forEach(b -> blanks.add(b.get(0).asText()));
                    yield blanks;
                }
                case "REMETTRE_ORDRE" -> {
                    ArrayNode order = json.createArrayNode();
                    IntStream.range(0, payload.path("lines").size()).forEach(order::add);
                    yield order;
                }
                default -> TextNode.valueOf((String) row.get("solution"));
            };
            try {
                var result = exercises.submit(userId, slug, answer, 0);
                if (!result.success()) {
                    failures.add(slug + " : " + result.message() + " " + result.feedback());
                }
            } catch (RuntimeException e) {
                failures.add(slug + " : exception " + e.getMessage());
            }
            if (kind.equals("CORRIGER_ERREUR") || payload.hasNonNull("starter") && kind.equals("SQL")
                    && slug.contains("corriger")) {
                var starter = exercises.submit(userId, slug, TextNode.valueOf(payload.path("starter").asText()), 0);
                if (starter.success()) {
                    failures.add(slug + " : le code de départ (erroné) est accepté");
                }
            }
        }
        assertThat(failures).as("Exercices dont la correction n'est pas acceptée").isEmpty();
    }
}
