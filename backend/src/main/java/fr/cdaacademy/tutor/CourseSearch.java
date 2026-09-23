package fr.cdaacademy.tutor;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Recherche plein texte simple dans les contenus des cours (définitions, fiches « à retenir »,
 * questions du jury). Sert de réponse de secours quand l'assistant IA local n'est pas disponible.
 */
@Component
public class CourseSearch {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Set<String> STOP_WORDS = Set.of(
            "quoi", "comment", "pourquoi", "quel", "quelle", "quels", "quelles", "est", "cest", "une", "des", "les",
            "dans", "avec", "pour", "sans", "sur", "sous", "que", "qui", "faire", "fait", "entre", "difference",
            "cela", "ceci", "mon", "mes", "ton", "tes", "son", "ses", "leur", "leurs", "elle", "il", "nous", "vous",
            "peux", "peut", "dois", "doit", "veut", "veux", "sert", "servir", "explique", "expliquer", "moi", "bien",
            "plus", "moins", "tres", "aussi", "donc", "mais", "alors", "avoir", "etre", "the", "and", "what", "how");

    public record Snippet(String kind, String title, String text, String lessonSlug, String lessonTitle) {
    }

    public record Hit(Snippet snippet, int score) {
    }

    private final JdbcTemplate jdbc;
    private volatile List<Snippet> cache = List.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public CourseSearch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Hit> search(String question, int limit) {
        Set<String> words = keywords(question);
        if (words.isEmpty()) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (Snippet s : snippets()) {
            String title = normalize(s.title());
            String text = normalize(s.text());
            int score = 0;
            for (String w : words) {
                if (title.contains(w)) {
                    score += 4;
                }
                if (text.contains(w)) {
                    score += 1;
                }
            }
            if (score >= 2 || (words.size() == 1 && score >= 1)) {
                hits.add(new Hit(s, score + ("DEFINITION".equals(s.kind()) ? 1 : 0)));
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed());
        return hits.subList(0, Math.min(limit, hits.size()));
    }

    static Set<String> keywords(String question) {
        Set<String> words = new HashSet<>();
        for (String w : normalize(question).split("[^a-z0-9]+")) {
            if ((w.length() >= 3 || w.equals("js") || w.equals("ui")) && !STOP_WORDS.contains(w)) {
                words.add(w.length() > 4 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
            }
        }
        return words;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private List<Snippet> snippets() {
        if (Instant.now().isBefore(loadedAt.plus(CACHE_TTL)) && !cache.isEmpty()) {
            return cache;
        }
        List<Snippet> list = new ArrayList<>();
        jdbc.query("""
                select b ->> 'term', b ->> 'md', l.slug, l.title
                from lessons l join modules m on m.id = l.module_id join courses c on c.id = m.course_id and c.published
                cross join lateral jsonb_array_elements(l.blocks) b
                where l.published and b ->> 'type' = 'definition'
                """, rs -> {
            list.add(new Snippet("DEFINITION", rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        });
        jdbc.query("""
                select l.title, l.memo, l.slug, l.title
                from lessons l join modules m on m.id = l.module_id join courses c on c.id = m.course_id and c.published
                where l.published and l.memo is not null
                """, rs -> {
            list.add(new Snippet("FICHE", "À retenir — " + rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4)));
        });
        jdbc.query("""
                select item ->> 'q', item ->> 'a', l.slug, l.title
                from lessons l join modules m on m.id = l.module_id join courses c on c.id = m.course_id and c.published
                cross join lateral jsonb_array_elements(l.blocks) b
                cross join lateral jsonb_array_elements(b -> 'items') item
                where l.published and b ->> 'type' = 'jury'
                """, rs -> {
            list.add(new Snippet("JURY", rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        });
        cache = List.copyOf(list);
        loadedAt = Instant.now();
        return cache;
    }
}
