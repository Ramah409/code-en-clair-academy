package fr.cdaacademy.exercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Remettre des lignes de code dans l'ordre.
 * Payload : { language, lines: ["ligne 1", "ligne 2", …] } (dans le bon ordre),
 *           alternatives : autres ordres acceptés, exprimés en index.
 */
@Component
public class OrderLinesValidator implements ExerciseValidator {

    private final ObjectMapper json;

    public OrderLinesValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Set<String> kinds() {
        return Set.of("REMETTRE_ORDRE");
    }

    @Override
    public JsonNode publicPayload(Exercise ex) {
        JsonNode lines = ex.payload().path("lines");
        List<Integer> order = new ArrayList<>(IntStream.range(0, lines.size()).boxed().toList());
        Random random = new Random(ex.id());
        do {
            Collections.shuffle(order, random);
        } while (lines.size() > 1 && isIdentity(order));
        ObjectNode p = json.createObjectNode();
        p.put("language", ex.payload().path("language").asText("text"));
        ArrayNode shuffled = p.putArray("lines");
        for (int index : order) {
            shuffled.addObject().put("id", index).put("text", lines.get(index).asText());
        }
        return p;
    }

    @Override
    public ValidationResult validate(Exercise ex, JsonNode answer) {
        int n = ex.payload().path("lines").size();
        List<Integer> given = new ArrayList<>();
        if (answer != null && answer.isArray()) {
            answer.forEach(a -> given.add(a.asInt(-1)));
        }
        if (given.size() != n) {
            return ValidationResult.ko("Place toutes les lignes avant de valider.", List.of(), null);
        }
        if (isIdentity(given) || matchesAlternative(ex, given)) {
            return ValidationResult.ok("Bravo ! Les lignes sont dans le bon ordre.", null);
        }
        long wellPlaced = IntStream.range(0, n).filter(i -> given.get(i) == i).count();
        return ValidationResult.ko(wellPlaced + " ligne(s) sur " + n + " à la bonne place.",
                List.of("Relis le code dans l'ordre d'exécution : une déclaration doit précéder son utilisation."),
                null);
    }

    private static boolean matchesAlternative(Exercise ex, List<Integer> given) {
        for (JsonNode alt : ex.payload().path("alternatives")) {
            List<Integer> order = new ArrayList<>();
            alt.forEach(i -> order.add(i.asInt()));
            if (order.equals(given)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdentity(List<Integer> order) {
        return IntStream.range(0, order.size()).allMatch(i -> order.get(i) == i);
    }
}
