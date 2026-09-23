package fr.cdaacademy.exercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Correction d'un MCD (Merise) construit dans l'éditeur visuel.
 *
 * Réponse : { entities: [{ id, name, attributes: [{ name, identifier }] }],
 *             associations: [{ id, name, attributes: [{ name }], links: [{ entity, card }] }] }
 *
 * Payload attendu : { expected: { entities: [{ name, aliases, attributes: [...] }],
 *                                 associations: [{ entities: [...], cards: { Entité: "0,n" }, attributes: [...] }] } }
 *
 * Le nom des associations est libre (plusieurs verbes conviennent) : on vérifie les entités reliées,
 * les cardinalités et les attributs portés par l'association.
 */
@Component
public class McdValidator implements ExerciseValidator {

    /** Écart constaté, avec un statut pour l'affichage (entité, identifiant, association…). */
    public record Check(String element, boolean ok, String message) {
    }

    private record Entity(String id, String name, String key, List<String> attributes, int identifiers) {
    }

    private final ObjectMapper json;

    public McdValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Set<String> kinds() {
        return Set.of("MCD");
    }

    @Override
    public JsonNode publicPayload(Exercise ex) {
        ObjectNode p = json.createObjectNode();
        p.put("editor", "mcd");
        p.set("starter", ex.payload().path("starter"));
        p.put("entityCount", ex.payload().path("expected").path("entities").size());
        p.put("associationCount", ex.payload().path("expected").path("associations").size());
        return p;
    }

    @Override
    public ValidationResult validate(Exercise ex, JsonNode answer) {
        if (answer == null || !answer.path("entities").isArray()) {
            return ValidationResult.ko("Construis ton MCD dans l'éditeur avant de le valider.", List.of(), null);
        }
        JsonNode expected = ex.payload().path("expected");
        List<Check> checks = new ArrayList<>();

        // Entités de l'apprenante
        Map<String, Entity> byId = new HashMap<>();
        Map<String, Entity> byKey = new HashMap<>();
        for (JsonNode e : answer.path("entities")) {
            List<String> attrs = new ArrayList<>();
            int ids = 0;
            for (JsonNode a : e.path("attributes")) {
                attrs.add(MeriseNames.normalize(a.path("name").asText()));
                if (a.path("identifier").asBoolean(false)) {
                    ids++;
                }
            }
            Entity entity = new Entity(e.path("id").asText(), e.path("name").asText(),
                    MeriseNames.normalize(e.path("name").asText()), attrs, ids);
            byId.put(entity.id(), entity);
            byKey.put(entity.key(), entity);
        }

        // 1. Entités attendues, identifiants et attributs
        Map<String, Entity> matched = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (JsonNode spec : expected.path("entities")) {
            String label = MeriseNames.display(spec);
            Entity found = MeriseNames.accepted(spec).stream().map(byKey::get).filter(e -> e != null).findFirst()
                    .orElse(null);
            if (found == null) {
                checks.add(new Check(label, false, "L'entité « " + label + " » est absente (ou porte un autre nom)."));
                continue;
            }
            matched.put(MeriseNames.normalize(label), found);
            used.add(found.key());
            if (found.identifiers() == 0) {
                checks.add(new Check(label, false, "L'entité « " + found.name()
                        + " » n'a pas d'identifiant : coche l'attribut qui identifie chaque occurrence."));
            }
            List<String> missing = new ArrayList<>();
            for (JsonNode attr : spec.path("attributes")) {
                if (MeriseNames.accepted(attr).stream().noneMatch(found.attributes()::contains)) {
                    missing.add(MeriseNames.display(attr));
                }
            }
            if (!missing.isEmpty()) {
                checks.add(new Check(label, false, "Il manque dans « " + found.name() + " » : " + String.join(", ", missing) + "."));
            } else if (found.identifiers() > 0) {
                checks.add(new Check(label, true, "Entité « " + found.name() + " » correcte."));
            }
        }
        for (Entity e : byId.values()) {
            if (!used.contains(e.key())) {
                checks.add(new Check(e.name(), false, "L'entité « " + e.name() + " » n'est pas attendue : "
                        + "est-ce un attribut d'une autre entité, ou une association ?"));
            }
        }

        // 2. Associations : entités reliées, cardinalités, attributs portés
        List<JsonNode> answerAssocs = new ArrayList<>();
        answer.path("associations").forEach(answerAssocs::add);
        Set<JsonNode> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (JsonNode spec : expected.path("associations")) {
            List<String> ends = new ArrayList<>();
            spec.path("entities").forEach(n -> ends.add(MeriseNames.normalize(n.asText())));
            String label = String.join(" – ", namesOf(spec));
            Map<String, String> cards = new HashMap<>();
            spec.path("cards").fields().forEachRemaining(f -> cards.put(MeriseNames.normalize(f.getKey()),
                    f.getValue().asText()));
            JsonNode assoc = findAssociation(answerAssocs, consumed, ends, matched, byId);
            if (assoc == null) {
                checks.add(new Check(label, false, "Il manque l'association entre " + label + "."));
                continue;
            }
            consumed.add(assoc);
            List<String> problems = new ArrayList<>();
            if (spec.has("cardsList")) {
                // Association réflexive : les deux pattes relient la même entité, on compare les cardinalités sans ordre
                List<String> wanted = new ArrayList<>();
                spec.path("cardsList").forEach(c -> wanted.add(MeriseNames.cardinality(c.asText())));
                List<String> given = new ArrayList<>();
                assoc.path("links").forEach(l -> given.add(MeriseNames.cardinality(l.path("card").asText())));
                wanted.sort(String::compareTo);
                given.sort(String::compareTo);
                if (!wanted.equals(given)) {
                    problems.add("cardinalités " + given + " au lieu de " + wanted);
                }
            }
            for (JsonNode link : spec.has("cardsList") ? json.createArrayNode() : assoc.path("links")) {
                Entity e = byId.get(link.path("entity").asText());
                if (e == null) {
                    continue;
                }
                String expectedCard = cards.get(expectedKeyFor(e, matched));
                if (expectedCard != null && !MeriseNames.cardinality(expectedCard)
                        .equals(MeriseNames.cardinality(link.path("card").asText()))) {
                    problems.add("côté « " + e.name() + " » : " + link.path("card").asText("?") + " au lieu de "
                            + expectedCard);
                }
            }
            List<String> assocAttrs = new ArrayList<>();
            assoc.path("attributes").forEach(a -> assocAttrs.add(MeriseNames.normalize(a.path("name").asText())));
            for (JsonNode attr : spec.path("attributes")) {
                if (MeriseNames.accepted(attr).stream().noneMatch(assocAttrs::contains)) {
                    problems.add("l'attribut « " + MeriseNames.display(attr) + " » doit être porté par l'association");
                }
            }
            String name = assoc.path("name").asText("(sans nom)");
            if (problems.isEmpty()) {
                checks.add(new Check(label, true, "Association « " + name + " » correcte."));
            } else {
                checks.add(new Check(label, false, "Association « " + name + " » : " + String.join(" ; ", problems) + "."));
            }
        }
        for (JsonNode a : answerAssocs) {
            if (!consumed.contains(a)) {
                checks.add(new Check(a.path("name").asText(), false,
                        "L'association « " + a.path("name").asText() + " » n'est pas attendue dans ce modèle."));
            }
        }

        List<String> problems = checks.stream().filter(c -> !c.ok()).map(Check::message).toList();
        if (problems.isEmpty()) {
            return ValidationResult.ok("Bravo ! Ton MCD est conforme : entités, identifiants, associations et cardinalités.",
                    checks);
        }
        return ValidationResult.ko(problems.size() + " point(s) à revoir dans ton MCD.", problems, checks);
    }

    private static List<String> namesOf(JsonNode spec) {
        List<String> names = new ArrayList<>();
        spec.path("entities").forEach(n -> names.add(n.asText()));
        return names;
    }

    /** Clé normalisée du nom attendu correspondant à une entité de l'apprenante. */
    private static String expectedKeyFor(Entity e, Map<String, Entity> matched) {
        return matched.entrySet().stream().filter(m -> m.getValue() == e).map(Map.Entry::getKey).findFirst()
                .orElse(e.key());
    }

    /** Association reliant exactement le même ensemble d'entités (réflexive : même entité deux fois). */
    private static JsonNode findAssociation(List<JsonNode> assocs, Set<JsonNode> consumed, List<String> ends,
            Map<String, Entity> matched, Map<String, Entity> byId) {
        List<String> wanted = new ArrayList<>();
        for (String end : ends) {
            Entity e = matched.get(end);
            wanted.add(e == null ? "?" + end : e.id());
        }
        wanted.sort(String::compareTo);
        for (JsonNode a : assocs) {
            if (consumed.contains(a)) {
                continue;
            }
            List<String> linked = new ArrayList<>();
            a.path("links").forEach(l -> linked.add(l.path("entity").asText()));
            linked.sort(String::compareTo);
            if (linked.equals(wanted)) {
                return a;
            }
        }
        return null;
    }
}
