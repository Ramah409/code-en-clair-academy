package fr.cdaacademy.exercise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Correction du passage MCD → MLD, saisi dans l'éditeur de tables.
 *
 * Réponse : { tables: [{ name, columns: [{ name, pk, fk }] }] } où fk contient le nom de la table référencée.
 *
 * Payload : { mcd: <MCD affiché>, expected: { tables: [{ name, aliases, pkCount, pkFks: [tables],
 *             fks: [tables], columns: [...] }] } }
 *
 * Les noms de colonnes clés sont libres (id_client, client_id…) : on vérifie la structure
 * (nombre de colonnes de clé primaire, tables référencées par les clés étrangères, colonnes attendues).
 */
@Component
public class MldValidator implements ExerciseValidator {

    public record Check(String element, boolean ok, String message) {
    }

    private final ObjectMapper json;

    public MldValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Set<String> kinds() {
        return Set.of("MCD_VERS_MLD");
    }

    @Override
    public JsonNode publicPayload(Exercise ex) {
        ObjectNode p = json.createObjectNode();
        p.put("editor", "mld");
        p.set("mcd", ex.payload().path("mcd"));
        p.set("starter", ex.payload().path("starter"));
        p.put("tableCount", ex.payload().path("expected").path("tables").size());
        return p;
    }

    @Override
    public ValidationResult validate(Exercise ex, JsonNode answer) {
        if (answer == null || !answer.path("tables").isArray() || answer.path("tables").isEmpty()) {
            return ValidationResult.ko("Ajoute au moins une table avant de valider.", List.of(), null);
        }
        JsonNode expectedTables = ex.payload().path("expected").path("tables");

        // Nom canonique (normalisé) de chaque table attendue, et table correspondante via les alias
        Map<String, String> canonical = new HashMap<>();
        for (JsonNode spec : expectedTables) {
            String key = MeriseNames.normalize(MeriseNames.display(spec));
            MeriseNames.accepted(spec).forEach(alias -> canonical.put(alias, key));
        }
        Map<String, JsonNode> userTables = new HashMap<>();
        for (JsonNode t : answer.path("tables")) {
            userTables.put(canonical.getOrDefault(MeriseNames.normalize(t.path("name").asText()),
                    "?" + MeriseNames.normalize(t.path("name").asText())), t);
        }

        List<Check> checks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode spec : expectedTables) {
            String label = MeriseNames.display(spec);
            String key = MeriseNames.normalize(label);
            JsonNode table = userTables.get(key);
            if (table == null) {
                checks.add(new Check(label, false, "La table « " + label + " » est absente (ou porte un autre nom)."));
                continue;
            }
            seen.add(key);
            List<String> problems = new ArrayList<>();

            List<JsonNode> pk = new ArrayList<>();
            List<String> fkTargets = new ArrayList<>();
            List<String> pkFkTargets = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            for (JsonNode c : table.path("columns")) {
                columns.add(MeriseNames.normalize(c.path("name").asText()));
                String target = c.path("fk").asText("");
                String targetKey = target.isBlank() ? null
                        : canonical.getOrDefault(MeriseNames.normalize(target), MeriseNames.normalize(target));
                if (c.path("pk").asBoolean(false)) {
                    pk.add(c);
                    if (targetKey != null) {
                        pkFkTargets.add(targetKey);
                    }
                }
                if (targetKey != null) {
                    fkTargets.add(targetKey);
                }
            }

            int pkCount = spec.path("pkCount").asInt(1);
            if (pk.isEmpty()) {
                problems.add("aucune clé primaire");
            } else if (pk.size() != pkCount) {
                problems.add("clé primaire composée de " + pk.size() + " colonne(s) au lieu de " + pkCount);
            }
            List<String> expectedFks = normalizedList(spec.path("fks"), canonical);
            List<String> sortedFks = new ArrayList<>(fkTargets);
            sortedFks.sort(String::compareTo);
            if (!expectedFks.equals(sortedFks)) {
                problems.add(describeFks(expectedFks, sortedFks));
            }
            List<String> expectedPkFks = normalizedList(spec.path("pkFks"), canonical);
            List<String> sortedPkFks = new ArrayList<>(pkFkTargets);
            sortedPkFks.sort(String::compareTo);
            if (!expectedPkFks.isEmpty() && !expectedPkFks.equals(sortedPkFks)) {
                problems.add("la clé primaire doit être composée des clés étrangères vers " + String.join(" et ", expectedPkFks));
            }
            List<String> missing = new ArrayList<>();
            for (JsonNode col : spec.path("columns")) {
                if (MeriseNames.accepted(col).stream().noneMatch(columns::contains)) {
                    missing.add(MeriseNames.display(col));
                }
            }
            if (!missing.isEmpty()) {
                problems.add("colonne(s) manquante(s) : " + String.join(", ", missing));
            }
            checks.add(problems.isEmpty() ? new Check(label, true, "Table « " + label + " » correcte.")
                    : new Check(label, false, "Table « " + table.path("name").asText() + " » : " + String.join(" ; ", problems) + "."));
        }
        for (Map.Entry<String, JsonNode> e : userTables.entrySet()) {
            if (!seen.contains(e.getKey())) {
                checks.add(new Check(e.getValue().path("name").asText(), false, "La table « "
                        + e.getValue().path("name").asText() + " » n'est pas attendue : vérifie les règles de passage."));
            }
        }

        List<String> problems = checks.stream().filter(c -> !c.ok()).map(Check::message).toList();
        if (problems.isEmpty()) {
            return ValidationResult.ok("Bravo ! Ton MLD respecte toutes les règles de passage.", checks);
        }
        return ValidationResult.ko(problems.size() + " point(s) à revoir dans ton MLD.", problems, checks);
    }

    private static List<String> normalizedList(JsonNode names, Map<String, String> canonical) {
        List<String> list = new ArrayList<>();
        names.forEach(n -> {
            String key = MeriseNames.normalize(n.asText());
            list.add(canonical.getOrDefault(key, key));
        });
        list.sort(String::compareTo);
        return list;
    }

    private static String describeFks(List<String> expected, List<String> given) {
        if (expected.isEmpty()) {
            return "cette table ne doit pas contenir de clé étrangère";
        }
        if (given.isEmpty()) {
            return "il manque la ou les clés étrangères vers : " + String.join(", ", expected);
        }
        return "clés étrangères vers " + given + " au lieu de " + expected;
    }
}
