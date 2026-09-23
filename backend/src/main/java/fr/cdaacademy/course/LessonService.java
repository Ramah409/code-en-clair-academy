package fr.cdaacademy.course;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.course.CourseAccess.ChapterState;
import fr.cdaacademy.course.CourseAccess.CourseState;
import fr.cdaacademy.course.CourseAccess.LessonState;
import fr.cdaacademy.course.CourseAccess.LessonStatus;
import fr.cdaacademy.exercise.ExerciseService;
import fr.cdaacademy.progress.ProgressService;
import fr.cdaacademy.progress.ProgressService.Reward;
import fr.cdaacademy.quiz.QuestionBank;
import fr.cdaacademy.quiz.QuestionBank.Answer;
import fr.cdaacademy.quiz.QuestionBank.Feedback;
import fr.cdaacademy.quiz.QuestionBank.Question;
import fr.cdaacademy.quiz.QuestionStats;

/**
 * Déroulé d'une leçon : blocs de cours, questions interactives corrigées immédiatement,
 * exercices pratiques et validation finale.
 *
 * Une leçon n'est validée que si chaque question du cours a reçu une bonne réponse et si
 * chaque exercice a été réussi.
 */
@Service
public class LessonService {

    /** XP pour la première bonne réponse à une question du cours. */
    static final int QUESTION_XP = 2;

    public record Ref(String slug, String title) {
    }

    public record ChapterRef(String slug, String title, String level, int position) {
    }

    public record LessonView(String slug, String title, String objective, String prerequisites, int duration,
            int xp, String memo, String commonMistakes, Ref course, ChapterRef chapter, int position, int lessonCount,
            JsonNode blocks, String status, int currentStep, Ref previous, Ref next, boolean chapterQuizNext) {
    }

    public record AnswerResult(Feedback feedback, int xpEarned, Reward reward) {
    }

    public record Completion(Reward reward, boolean alreadyDone, Ref next, boolean chapterQuizAvailable,
            String chapterSlug, String courseSlug) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CourseAccess access;
    private final QuestionBank bank;
    private final QuestionStats stats;
    private final ExerciseService exercises;
    private final ProgressService progress;

    public LessonService(JdbcTemplate jdbc, ObjectMapper json, CourseAccess access, QuestionBank bank,
            QuestionStats stats, ExerciseService exercises, ProgressService progress) {
        this.jdbc = jdbc;
        this.json = json;
        this.access = access;
        this.bank = bank;
        this.stats = stats;
        this.exercises = exercises;
        this.progress = progress;
    }

    private record LessonRow(long id, String slug, String title, String objective, String prerequisites,
            int duration, int xp, String memo, String commonMistakes, String blocks, long moduleId) {
    }

    // ------------------------------------------------------------------ lecture

    @Transactional
    public LessonView view(long userId, boolean admin, String slug) {
        CourseState course = access.loadByLesson(userId, admin, slug);
        Located located = locate(course, slug);
        if (located.lesson().status() == LessonStatus.LOCKED) {
            throw new BusinessRuleException(located.chapter().unlocked()
                    ? "Leçon verrouillée : termine d'abord la leçon précédente."
                    : "Chapitre verrouillé : réussis d'abord le QCM du chapitre précédent (80 % minimum).");
        }
        LessonRow row = row(slug);
        jdbc.update("""
                insert into progress (user_id, lesson_id, status, current_step) values (?, ?, 'EN_COURS', 0)
                on conflict (user_id, lesson_id) do update set updated_at = now()
                """, userId, row.id());
        Map<String, Object> p = jdbc.queryForMap(
                "select status, current_step from progress where user_id = ? and lesson_id = ?", userId, row.id());

        List<LessonState> lessons = located.chapter().lessons();
        int index = lessons.indexOf(located.lesson());
        Ref previous = index > 0 ? ref(lessons.get(index - 1)) : previousChapterLast(course, located.chapter());
        Ref next = index < lessons.size() - 1 ? ref(lessons.get(index + 1)) : null;

        return new LessonView(row.slug(), row.title(), row.objective(), row.prerequisites(), row.duration(), row.xp(),
                row.memo(), row.commonMistakes(), new Ref(course.slug(), course.title()),
                new ChapterRef(located.chapter().slug(), located.chapter().title(), located.chapter().level(),
                        located.chapter().position()),
                index + 1, lessons.size(), enrichBlocks(userId, row), (String) p.get("status"),
                ((Number) p.get("current_step")).intValue(), previous, next, next == null);
    }

    /** Ajoute aux blocs « question » et « exercice » leur contenu public et l'état de l'apprenante. */
    private JsonNode enrichBlocks(long userId, LessonRow row) {
        ArrayNode blocks = (ArrayNode) parse(row.blocks());
        Map<String, Question> questions = new java.util.HashMap<>();
        for (Question q : bank.byLesson(row.id())) {
            questions.put(q.code(), q);
        }
        Set<Long> answered = answeredCorrectly(userId, row.id());
        for (JsonNode node : blocks) {
            ObjectNode block = (ObjectNode) node;
            switch (block.path("type").asText()) {
                case "question" -> {
                    Question q = questions.get(block.path("ref").asText());
                    if (q != null) {
                        block.set("question", json.valueToTree(QuestionBank.toPublic(q, null)));
                        block.put("answered", answered.contains(q.id()));
                    }
                }
                case "exercise" -> {
                    Long id = exerciseId(block.path("ref").asText());
                    if (id != null) {
                        block.set("exercise", json.valueToTree(exercises.viewById(userId, id)));
                    }
                }
                default -> {
                }
            }
        }
        return blocks;
    }

    // ------------------------------------------------------------------ interactions

    @Transactional
    public AnswerResult answer(long userId, boolean admin, String lessonSlug, String questionCode, Answer answer) {
        ensureAccessible(userId, admin, lessonSlug);
        LessonRow row = row(lessonSlug);
        Question q = bank.byCode(questionCode);
        if (q == null || q.lessonId() == null || q.lessonId() != row.id()) {
            throw new NotFoundException("Question introuvable dans cette leçon.");
        }
        if (answer == null || answer.isEmpty()) {
            throw new BusinessRuleException("Choisis ou saisis une réponse avant de valider.");
        }
        Feedback feedback = QuestionBank.grade(q, answer);
        boolean first = stats.record(userId, q.id(), feedback.correct());
        Reward reward = first ? progress.award(userId, QUESTION_XP, 0, false) : Reward.none();
        return new AnswerResult(feedback, first ? QUESTION_XP : 0, reward);
    }

    @Transactional
    public void saveStep(long userId, boolean admin, String lessonSlug, int step) {
        ensureAccessible(userId, admin, lessonSlug);
        jdbc.update("""
                update progress set current_step = greatest(current_step, ?), updated_at = now()
                where user_id = ? and lesson_id = (select id from lessons where slug = ?)
                """, Math.max(step, 0), userId, lessonSlug);
    }

    @Transactional
    public Completion complete(long userId, boolean admin, String lessonSlug, int secondsSpent) {
        CourseState course = ensureAccessible(userId, admin, lessonSlug);
        LessonRow row = row(lessonSlug);
        List<String> missing = missingRequirements(userId, row);
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Leçon pas encore validée : " + String.join(" ; ", missing) + ".");
        }
        int updated = jdbc.update("""
                insert into progress (user_id, lesson_id, status, completed_at) values (?, ?, 'TERMINEE', now())
                on conflict (user_id, lesson_id) do update set status = 'TERMINEE', completed_at = now(),
                    updated_at = now(), time_spent_seconds = progress.time_spent_seconds + ?
                where progress.status <> 'TERMINEE'
                """, userId, row.id(), Math.max(secondsSpent, 0));
        boolean alreadyDone = updated == 0;
        int cap = row.duration() * 60 * 2;
        Reward reward = progress.award(userId, alreadyDone ? 0 : row.xp(), Math.min(secondsSpent, cap), false);

        CourseState after = access.load(userId, admin, course.slug());
        Located located = locate(after, lessonSlug);
        List<LessonState> lessons = located.chapter().lessons();
        int index = lessons.indexOf(located.lesson());
        Ref next = index < lessons.size() - 1 ? ref(lessons.get(index + 1)) : null;
        return new Completion(reward, alreadyDone, next, located.chapter().quizAvailable(), located.chapter().slug(),
                course.slug());
    }

    private List<String> missingRequirements(long userId, LessonRow row) {
        List<String> missing = new ArrayList<>();
        Set<Long> answered = answeredCorrectly(userId, row.id());
        int unanswered = 0;
        int unsolved = 0;
        for (JsonNode block : parse(row.blocks())) {
            String type = block.path("type").asText();
            if (type.equals("question")) {
                Question q = bank.byCode(block.path("ref").asText());
                if (q != null && !answered.contains(q.id())) {
                    unanswered++;
                }
            } else if (type.equals("exercise")) {
                Long id = exerciseId(block.path("ref").asText());
                if (id != null && !exercises.isSolved(userId, id)) {
                    unsolved++;
                }
            }
        }
        if (unanswered > 0) {
            missing.add(unanswered + " question(s) du cours à réussir");
        }
        if (unsolved > 0) {
            missing.add(unsolved + " exercice(s) pratique(s) à valider");
        }
        return missing;
    }

    // ------------------------------------------------------------------ utilitaires

    private record Located(ChapterState chapter, LessonState lesson) {
    }

    private static Located locate(CourseState course, String slug) {
        for (ChapterState ch : course.chapters()) {
            for (LessonState l : ch.lessons()) {
                if (l.slug().equals(slug)) {
                    return new Located(ch, l);
                }
            }
        }
        throw new NotFoundException("Leçon introuvable.");
    }

    private CourseState ensureAccessible(long userId, boolean admin, String lessonSlug) {
        CourseState course = access.loadByLesson(userId, admin, lessonSlug);
        if (locate(course, lessonSlug).lesson().status() == LessonStatus.LOCKED) {
            throw new BusinessRuleException("Leçon verrouillée.");
        }
        return course;
    }

    private static Ref previousChapterLast(CourseState course, ChapterState chapter) {
        int i = course.chapters().indexOf(chapter);
        if (i <= 0 || course.chapters().get(i - 1).lessons().isEmpty()) {
            return null;
        }
        List<LessonState> prev = course.chapters().get(i - 1).lessons();
        return ref(prev.get(prev.size() - 1));
    }

    private static Ref ref(LessonState l) {
        return new Ref(l.slug(), l.title());
    }

    private Set<Long> answeredCorrectly(long userId, long lessonId) {
        return new HashSet<>(jdbc.queryForList("""
                select s.question_id from user_question_stats s join questions q on q.id = s.question_id
                where s.user_id = ? and q.lesson_id = ? and s.times_correct > 0
                """, Long.class, userId, lessonId));
    }

    private Long exerciseId(String slug) {
        List<Long> ids = jdbc.queryForList("select id from exercises where slug = ?", Long.class, slug);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private LessonRow row(String slug) {
        List<LessonRow> rows = jdbc.query("""
                select id, slug, title, objective, prerequisites, duration_minutes, xp_reward, memo, common_mistakes,
                       blocks, module_id from lessons where slug = ? and published
                """, (rs, i) -> new LessonRow(rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
                rs.getString("objective"), rs.getString("prerequisites"), rs.getInt("duration_minutes"),
                rs.getInt("xp_reward"), rs.getString("memo"), rs.getString("common_mistakes"),
                rs.getString("blocks"), rs.getLong("module_id")), slug);
        if (rows.isEmpty()) {
            throw new NotFoundException("Leçon introuvable.");
        }
        return rows.getFirst();
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
