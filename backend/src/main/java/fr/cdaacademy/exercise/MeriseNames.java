package fr.cdaacademy.exercise;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

/** Comparaison tolérante des noms de modélisation (casse, accents, espaces, pluriel simple). */
final class MeriseNames {

    private MeriseNames() {
    }

    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (s.length() > 3 && (s.endsWith("s") || s.endsWith("x"))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Nom attendu et ses variantes acceptées : "nom" ou { name, aliases: [...] }. */
    static List<String> accepted(JsonNode spec) {
        List<String> names = new ArrayList<>();
        if (spec.isTextual()) {
            names.add(normalize(spec.asText()));
        } else {
            names.add(normalize(spec.path("name").asText()));
            spec.path("aliases").forEach(a -> names.add(normalize(a.asText())));
        }
        return names;
    }

    static String display(JsonNode spec) {
        return spec.isTextual() ? spec.asText() : spec.path("name").asText();
    }

    /** Cardinalités : « 0,n », « 0..N », « 0-n » → « 0,n ». */
    static String cardinality(String card) {
        if (card == null) {
            return "";
        }
        String c = card.strip().toLowerCase(Locale.ROOT).replace("..", ",").replace("-", ",").replace(";", ",")
                .replace(" ", "").replace("*", "n");
        return c;
    }
}
