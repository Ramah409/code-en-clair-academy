package fr.cdaacademy.exercise;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Exercices de code (Java, TypeScript, Angular, HTML, YAML, Dockerfile…) corrigés par
 * vérifications automatiques : chaque règle décrit un élément attendu ou interdit dans le code.
 * Le code n'est jamais exécuté sur le serveur.
 *
 * Payload : { language, starter, output, checks: [{ pattern, message, absent }] }
 */
@Component
public class CodeChecksValidator implements ExerciseValidator {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)(^|[^:\"'])//.*$");
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HASH_COMMENT = Pattern.compile("(?m)^\\s*#.*$");

    /** Limite la taille du code analysé par les expressions régulières des vérifications. */
    static final int MAX_CODE_LENGTH = 20_000;

    private final ObjectMapper json;

    public CodeChecksValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Set<String> kinds() {
        return Set.of("CODE_LIBRE", "CORRIGER_ERREUR");
    }

    @Override
    public JsonNode publicPayload(Exercise ex) {
        ObjectNode p = json.createObjectNode();
        p.put("language", ex.payload().path("language").asText("text"));
        p.put("starter", ex.payload().path("starter").asText(""));
        p.put("checkCount", ex.payload().path("checks").size());
        return p;
    }

    @Override
    public ValidationResult validate(Exercise ex, JsonNode answer) {
        String code = answer == null ? "" : answer.asText("");
        if (code.isBlank()) {
            return ValidationResult.ko("Écris ton code avant de le valider.", List.of(), null);
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return ValidationResult.ko("Ton code dépasse " + MAX_CODE_LENGTH + " caractères : garde seulement ce que "
                    + "demande l'énoncé.", List.of(), null);
        }
        String language = ex.payload().path("language").asText("text");
        String cleaned = stripComments(code, language);
        List<String> problems = new ArrayList<>();
        int passed = 0;
        for (JsonNode check : ex.payload().path("checks")) {
            Pattern p = Pattern.compile(check.path("pattern").asText(), Pattern.DOTALL | Pattern.MULTILINE);
            boolean found = p.matcher(cleaned).find();
            boolean ok = check.path("absent").asBoolean(false) != found;
            if (ok) {
                passed++;
            } else {
                problems.add(check.path("message").asText("Une vérification n'est pas satisfaite."));
            }
        }
        record Details(int passed, int total, String output) {
        }
        int total = ex.payload().path("checks").size();
        if (problems.isEmpty()) {
            return ValidationResult.ok("Bravo ! Toutes les vérifications sont réussies (" + total + "/" + total + ").",
                    new Details(passed, total, ex.payload().path("output").asText(null)));
        }
        return ValidationResult.ko(passed + " vérification(s) réussie(s) sur " + total + ".", problems,
                new Details(passed, total, null));
    }

    static String stripComments(String code, String language) {
        return switch (language) {
            case "html" -> HTML_COMMENT.matcher(code).replaceAll("");
            case "yaml", "dockerfile", "bash", "properties" -> HASH_COMMENT.matcher(code).replaceAll("");
            case "sql" -> code.replaceAll("--[^\\n]*", "");
            default -> LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(code).replaceAll("")).replaceAll("$1");
        };
    }
}
