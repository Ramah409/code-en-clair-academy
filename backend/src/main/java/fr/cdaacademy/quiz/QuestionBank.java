package fr.cdaacademy.quiz;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Accès à la banque de questions et correction des réponses.
 * La bonne réponse n'est jamais envoyée au navigateur avant que l'apprenante ait répondu.
 */
@Component
public class QuestionBank {

    public record Choice(int position, String label, boolean correct, String why) {
    }

    public record Question(long id, String code, String kind, String prompt, String snippet, String language,
            int difficulty, String explanation, String expectedAnswer, Long courseId, Long moduleId, Long lessonId,
            Long skillId, String skillName, List<Choice> choices) {

        List<String> acceptedAnswers() {
            return expectedAnswer == null ? List.of() : List.of(expectedAnswer.split("\n"));
        }
    }

    /** Question telle qu'affichée : sans la bonne réponse. */
    public record PublicChoice(int position, String label) {
    }

    public record PublicQuestion(long id, String code, String kind, String prompt, String snippet, String language,
            int difficulty, String theme, List<PublicChoice> choices) {
    }

    /** Réponse de l'apprenante : positions de choix (QCM) ou texte (code à compléter). */
    public record Answer(List<Integer> choices, String text) {
        public boolean isEmpty() {
            return (choices == null || choices.isEmpty()) && (text == null || text.isBlank());
        }
    }

    public record ChoiceFeedback(int position, String label, boolean correct, boolean selected, String why) {
    }

    /** Correction détaillée : bonne réponse, explication générale et explication de chaque choix. */
    public record Feedback(boolean correct, String explanation, List<ChoiceFeedback> choices,
            List<String> acceptedAnswers, String givenText) {
    }

    private final JdbcTemplate jdbc;

    public QuestionBank(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ lecture

    public Map<Long, Question> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return load("q.id = any (?)", (Object) ids.toArray(Long[]::new));
    }

    public Question byCode(String code) {
        Map<Long, Question> found = load("q.code = ?", code);
        return found.isEmpty() ? null : found.values().iterator().next();
    }

    public List<Question> byLesson(long lessonId) {
        return new ArrayList<>(load("q.lesson_id = ?", lessonId).values());
    }

    /** @param where condition SQL fixe (jamais construite à partir d'une saisie), paramétrée par {@code args} */
    private Map<Long, Question> load(String where, Object... args) {
        Map<Long, List<Choice>> choices = new LinkedHashMap<>();
        jdbc.query("select c.question_id, c.position, c.label, c.correct, c.explanation from choices c "
                + "join questions q on q.id = c.question_id where " + where + " order by c.question_id, c.position",
                rs -> {
                    choices.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(
                            new Choice(rs.getInt(2), rs.getString(3), rs.getBoolean(4), rs.getString(5)));
                }, args);
        Map<Long, Question> result = new LinkedHashMap<>();
        jdbc.query("""
                select q.id, q.code, q.kind, q.prompt, q.code_snippet, q.code_language, q.difficulty, q.explanation,
                       q.expected_answer, q.course_id, q.module_id, q.lesson_id, q.skill_id, s.name as skill_name
                from questions q left join skills s on s.id = q.skill_id
                where\s""" + where + " order by q.position, q.id", rs -> {
            long id = rs.getLong("id");
            result.put(id, new Question(id, rs.getString("code"), rs.getString("kind"), rs.getString("prompt"),
                    rs.getString("code_snippet"), rs.getString("code_language"), rs.getInt("difficulty"),
                    rs.getString("explanation"), rs.getString("expected_answer"),
                    (Long) rs.getObject("course_id"), (Long) rs.getObject("module_id"),
                    (Long) rs.getObject("lesson_id"), (Long) rs.getObject("skill_id"), rs.getString("skill_name"),
                    choices.getOrDefault(id, List.of())));
        }, args);
        return result;
    }

    // ------------------------------------------------------------------ présentation

    public static PublicQuestion toPublic(Question q, List<Integer> order) {
        Map<Integer, Choice> byPosition = q.choices().stream()
                .collect(Collectors.toMap(Choice::position, c -> c));
        List<PublicChoice> choices = (order == null || order.isEmpty() ? q.choices().stream().map(Choice::position).toList()
                : order).stream()
                .filter(byPosition::containsKey)
                .map(p -> new PublicChoice(p, byPosition.get(p).label()))
                .toList();
        return new PublicQuestion(q.id(), q.code(), q.kind(), q.prompt(), q.snippet(), q.language(), q.difficulty(),
                q.skillName(), choices);
    }

    // ------------------------------------------------------------------ correction

    public static Feedback grade(Question q, Answer answer) {
        boolean textKind = q.kind().equals("COMPLETER_CODE") || q.kind().equals("TEXTE");
        if (textKind) {
            String given = answer == null || answer.text() == null ? "" : answer.text();
            boolean correct = q.acceptedAnswers().stream().anyMatch(a -> sameText(a, given, q.language()));
            return new Feedback(correct, q.explanation(), List.of(), q.acceptedAnswers(), given);
        }
        Set<Integer> selected = new HashSet<>(answer == null || answer.choices() == null ? List.of() : answer.choices());
        Set<Integer> expected = q.choices().stream().filter(Choice::correct).map(Choice::position)
                .collect(Collectors.toSet());
        boolean correct = selected.equals(expected);
        List<ChoiceFeedback> details = q.choices().stream()
                .map(c -> new ChoiceFeedback(c.position(), c.label(), c.correct(), selected.contains(c.position()),
                        c.why()))
                .toList();
        return new Feedback(correct, q.explanation(), details, List.of(), null);
    }

    /**
     * Comparaison tolérante d'un fragment de code : espaces ignorés, point-virgule final facultatif,
     * casse ignorée pour le SQL (les mots-clés SQL ne sont pas sensibles à la casse).
     */
    static boolean sameText(String expected, String given, String language) {
        String a = normalize(expected);
        String b = normalize(given);
        boolean caseInsensitive = language == null || language.equalsIgnoreCase("sql");
        return caseInsensitive ? a.equalsIgnoreCase(b) : a.equals(b);
    }

    private static String normalize(String s) {
        String t = s.strip();
        while (t.endsWith(";")) {
            t = t.substring(0, t.length() - 1).strip();
        }
        return t.replaceAll("\\s+", "").replace('\u2019', '\'');
    }
}
