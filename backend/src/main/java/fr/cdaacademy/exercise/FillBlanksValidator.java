package fr.cdaacademy.exercise;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Code à trous : le modèle contient des marqueurs [[1]], [[2]]… à compléter.
 * Payload : { language, template, blanks: [["réponse", "variante"], …], caseSensitive }
 */
@Component
public class FillBlanksValidator implements ExerciseValidator {

    public record BlankResult(int index, boolean correct) {
    }

    private final ObjectMapper json;

    public FillBlanksValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Set<String> kinds() {
        return Set.of("COMPLETER");
    }

    @Override
    public JsonNode publicPayload(Exercise ex) {
        ObjectNode p = json.createObjectNode();
        p.put("language", ex.payload().path("language").asText("text"));
        p.put("template", ex.payload().path("template").asText(""));
        p.put("blankCount", ex.payload().path("blanks").size());
        return p;
    }

    @Override
    public ValidationResult validate(Exercise ex, JsonNode answer) {
        JsonNode blanks = ex.payload().path("blanks");
        boolean caseSensitive = ex.payload().path("caseSensitive").asBoolean(
                !"sql".equals(ex.payload().path("language").asText()));
        List<BlankResult> results = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < blanks.size(); i++) {
            String given = answer != null && answer.isArray() && i < answer.size() ? answer.get(i).asText("") : "";
            boolean ok = false;
            for (JsonNode accepted : blanks.get(i)) {
                ok |= same(accepted.asText(), given, caseSensitive);
            }
            results.add(new BlankResult(i + 1, ok));
            if (!ok) {
                problems.add(given.isBlank() ? "Le trou n° " + (i + 1) + " est vide."
                        : "Le trou n° " + (i + 1) + " n'est pas correct (« " + given.strip() + " »).");
            }
        }
        if (problems.isEmpty()) {
            return ValidationResult.ok("Bravo ! Tous les trous sont correctement complétés.", results);
        }
        return ValidationResult.ko((blanks.size() - problems.size()) + " trou(s) correct(s) sur " + blanks.size() + ".",
                problems, results);
    }

    private static boolean same(String expected, String given, boolean caseSensitive) {
        String a = expected.replaceAll("\\s+", "");
        String b = given.replaceAll("\\s+", "");
        return caseSensitive ? a.equals(b) : a.equalsIgnoreCase(b);
    }
}
