package fr.cdaacademy.exercise;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.progress.ProgressService;
import fr.cdaacademy.progress.ProgressService.Reward;

/**
 * Exercices pratiques : consultation, indices graduels, correction détaillée et validation.
 *
 * XP d'un exercice réussi pour la première fois : récompense de base, diminuée de 20 % par
 * indice dévoilé, et réduite au quart si la correction a été consultée avant la réussite.
 */
@Service
public class ExerciseService {

    public record Context(String courseSlug, String courseTitle, String lessonSlug, String lessonTitle) {
    }

    public record ExerciseView(String slug, String title, String kind, String difficulty, String statement,
            String criteria, int xp, JsonNode payload, Context context, int hintsUsed, List<String> hints,
            boolean solutionViewed, boolean solutionAvailable, String solution, String explanation,
            String lastAnswer, boolean solved, int submissions) {
    }

    public record SubmitResult(boolean success, String message, List<String> feedback, Object details,
            boolean firstSuccess, Reward reward, String solution, String explanation) {
    }

    public record Hint(int index, String text, int remaining) {
    }

    public record CatalogItem(String slug, String title, String kind, String difficulty, int xp, String courseSlug,
            String courseTitle, String lessonSlug, String lessonTitle, boolean solved, boolean attempted) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProgressService progress;
    private final Map<String, ExerciseValidator> validators;

    public ExerciseService(JdbcTemplate jdbc, ObjectMapper json, ProgressService progress,
            List<ExerciseValidator> validators) {
        this.jdbc = jdbc;
        this.json = json;
        this.progress = progress;
        this.validators = validators.stream()
                .flatMap(v -> v.kinds().stream().map(k -> Map.entry(k, v)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public List<CatalogItem> catalog(long userId, String course, String kind, String difficulty) {
        return jdbc.query("""
                select e.slug, e.title, e.kind, e.difficulty, e.xp_reward, c.slug as course_slug,
                       c.title as course_title, l.slug as lesson_slug, l.title as lesson_title,
                       s.solved_at is not null as solved, coalesce(s.submissions, 0) > 0 as attempted
                from exercises e
                left join courses c on c.id = e.course_id
                left join lessons l on l.id = e.lesson_id
                left join modules m on m.id = l.module_id
                left join exercise_states s on s.exercise_id = e.id and s.user_id = ?
                where e.published and e.purpose = 'EXERCICE'
                  and (?::text is null or c.slug = ?) and (?::text is null or e.kind = ?)
                  and (?::text is null or e.difficulty = ?)
                order by c.level_number nulls last, m.position nulls last, l.position nulls last, e.position
                """, (rs, i) -> new CatalogItem(rs.getString("slug"), rs.getString("title"), rs.getString("kind"),
                rs.getString("difficulty"), rs.getInt("xp_reward"), rs.getString("course_slug"),
                rs.getString("course_title"), rs.getString("lesson_slug"), rs.getString("lesson_title"),
                rs.getBoolean("solved"), rs.getBoolean("attempted")),
                userId, course, course, kind, kind, difficulty, difficulty);
    }

    @Transactional(readOnly = true)
    public ExerciseView view(long userId, String slug) {
        Exercise ex = find(slug);
        return toView(userId, ex);
    }

    ExerciseView toView(long userId, Exercise ex) {
        State st = state(userId, ex.id());
        boolean solved = st.solved();
        boolean showSolution = solved || st.solutionViewed();
        Context ctx = jdbc.query("""
                select c.slug, c.title, l.slug, l.title from exercises e
                left join courses c on c.id = e.course_id left join lessons l on l.id = e.lesson_id where e.id = ?
                """, rs -> rs.next() ? new Context(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4)) : null, ex.id());
        return new ExerciseView(ex.slug(), ex.title(), ex.kind(), ex.difficulty(), ex.statement(), ex.criteria(),
                ex.xp(), validator(ex).publicPayload(ex), ctx, st.hintsUsed(), ex.hints().subList(0, st.hintsUsed()),
                st.solutionViewed(), canViewSolution(st), showSolution ? ex.solution() : null,
                showSolution ? ex.explanation() : null, st.lastAnswer(), solved, st.submissions());
    }

    /** Vue publique utilisable dans une leçon (identique, sans contexte). */
    public ExerciseView viewById(long userId, long exerciseId) {
        String slug = jdbc.queryForObject("select slug from exercises where id = ?", String.class, exerciseId);
        return view(userId, slug);
    }

    // ------------------------------------------------------------------ aides

    @Transactional
    public Hint nextHint(long userId, String slug) {
        Exercise ex = find(slug);
        State st = state(userId, ex.id());
        if (st.hintsUsed() >= 3) {
            throw new BusinessRuleException("Tous les indices ont déjà été dévoilés.");
        }
        int index = st.hintsUsed() + 1;
        jdbc.update("""
                insert into exercise_states (user_id, exercise_id, hints_used) values (?, ?, 1)
                on conflict (user_id, exercise_id) do update set hints_used = exercise_states.hints_used + 1,
                    updated_at = now()
                """, userId, ex.id());
        return new Hint(index, ex.hints().get(index - 1), 3 - index);
    }

    @Transactional
    public ExerciseView revealSolution(long userId, String slug) {
        Exercise ex = find(slug);
        State st = state(userId, ex.id());
        if (!canViewSolution(st)) {
            throw new BusinessRuleException(
                    "La correction se débloque après une première tentative ou après avoir consulté les trois indices.");
        }
        jdbc.update("""
                insert into exercise_states (user_id, exercise_id, solution_viewed) values (?, ?, true)
                on conflict (user_id, exercise_id) do update set solution_viewed = true, updated_at = now()
                """, userId, ex.id());
        return toView(userId, ex);
    }

    private static boolean canViewSolution(State st) {
        return st.solved() || st.submissions() >= 1 || st.hintsUsed() >= 3;
    }

    // ------------------------------------------------------------------ validation

    @Transactional
    public SubmitResult submit(long userId, String slug, JsonNode answer, int secondsSpent) {
        Exercise ex = find(slug);
        State before = state(userId, ex.id());
        ValidationResult result = validator(ex).validate(ex, answer);
        String answerText = answer == null ? "" : (answer.isTextual() ? answer.asText() : answer.toString());

        jdbc.update("""
                insert into exercise_states (user_id, exercise_id, submissions, last_answer, solved_at)
                values (?, ?, 1, ?, case when ? then now() end)
                on conflict (user_id, exercise_id) do update set submissions = exercise_states.submissions + 1,
                    last_answer = excluded.last_answer,
                    solved_at = coalesce(exercise_states.solved_at, excluded.solved_at), updated_at = now()
                """, userId, ex.id(), answerText, result.success());
        long attemptId = jdbc.queryForObject("""
                insert into attempts (user_id, exercise_id, submitted_at, score, max_score, success, hints_used,
                                      duration_seconds)
                values (?, ?, now(), ?, 1, ?, ?, ?) returning id
                """, Long.class, userId, ex.id(), result.success() ? 1 : 0, result.success(), before.hintsUsed(),
                Math.max(secondsSpent, 0));
        jdbc.update("insert into answers (attempt_id, given_answer, correct, feedback) values (?, ?, ?, ?)",
                attemptId, answerText, result.success(), String.join("\n", result.feedback()));

        boolean firstSuccess = result.success() && !before.solved();
        Reward reward = Reward.none();
        if (firstSuccess) {
            reward = progress.award(userId, xpFor(ex, before), secondsSpent, true);
        } else if (secondsSpent > 0) {
            reward = progress.award(userId, 0, secondsSpent, false);
        }
        boolean showSolution = result.success();
        return new SubmitResult(result.success(), result.message(), result.feedback(), result.details(), firstSuccess,
                reward, showSolution ? ex.solution() : null, showSolution ? ex.explanation() : null);
    }

    static int xpFor(Exercise ex, State st) {
        double xp = ex.xp() * (1 - 0.2 * st.hintsUsed());
        if (st.solutionViewed()) {
            xp = xp / 4;
        }
        return Math.max(1, (int) Math.round(xp));
    }

    // ------------------------------------------------------------------ accès aux données

    record State(int hintsUsed, boolean solutionViewed, int submissions, String lastAnswer, boolean solved) {
    }

    State state(long userId, long exerciseId) {
        return jdbc.query("""
                select hints_used, solution_viewed, submissions, last_answer, solved_at is not null
                from exercise_states where user_id = ? and exercise_id = ?
                """, rs -> rs.next() ? new State(rs.getInt(1), rs.getBoolean(2), rs.getInt(3), rs.getString(4),
                rs.getBoolean(5)) : new State(0, false, 0, null, false), userId, exerciseId);
    }

    public boolean isSolved(long userId, long exerciseId) {
        return state(userId, exerciseId).solved();
    }

    Exercise find(String slug) {
        List<Exercise> found = jdbc.query("""
                select e.id, e.slug, e.kind, e.difficulty, e.title, e.statement, e.success_criteria, e.hint_1,
                       e.hint_2, e.hint_3, e.solution, e.explanation, e.xp_reward, e.payload, e.lesson_id,
                       e.course_id, s.code as skill_code
                from exercises e left join skills s on s.id = e.skill_id
                where e.slug = ? and e.published
                """, (rs, i) -> new Exercise(rs.getLong("id"), rs.getString("slug"), rs.getString("kind"),
                rs.getString("difficulty"), rs.getString("title"), rs.getString("statement"),
                rs.getString("success_criteria"),
                List.of(rs.getString("hint_1"), rs.getString("hint_2"), rs.getString("hint_3")),
                rs.getString("solution"), rs.getString("explanation"), rs.getInt("xp_reward"),
                parse(rs.getString("payload")), (Long) rs.getObject("lesson_id"), (Long) rs.getObject("course_id"),
                rs.getString("skill_code")), slug);
        if (found.isEmpty()) {
            throw new NotFoundException("Exercice introuvable.");
        }
        return found.getFirst();
    }

    private ExerciseValidator validator(Exercise ex) {
        ExerciseValidator v = validators.get(ex.kind());
        if (v == null) {
            throw new BusinessRuleException("Ce type d'exercice (" + ex.kind() + ") n'est pas encore corrigé automatiquement.");
        }
        return v;
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
