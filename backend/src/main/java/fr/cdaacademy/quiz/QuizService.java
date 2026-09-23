package fr.cdaacademy.quiz;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.certificate.CertificateService;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.course.CourseAccess;
import fr.cdaacademy.course.CourseAccess.ChapterState;
import fr.cdaacademy.course.CourseAccess.CourseState;
import fr.cdaacademy.progress.ProgressService;
import fr.cdaacademy.progress.ProgressService.Reward;
import fr.cdaacademy.quiz.QuestionBank.Answer;
import fr.cdaacademy.quiz.QuestionBank.Feedback;
import fr.cdaacademy.quiz.QuestionBank.PublicQuestion;
import fr.cdaacademy.quiz.QuestionBank.Question;

/**
 * Moteur de QCM.
 * <ul>
 *   <li>CHAPITRE : QCM de fin de chapitre, 10 à 20 questions tirées de la banque du chapitre, seuil de 80 % ;</li>
 *   <li>PARCOURS : examen final de 30 à 50 questions, temps limité facultatif ;</li>
 *   <li>ENTRAINEMENT : matière, chapitre, niveau et nombre de questions au choix ;</li>
 *   <li>ERREURS : questions ratées et pas encore réussies deux fois de suite ;</li>
 *   <li>ALEATOIRE : mélange de plusieurs matières ;</li>
 *   <li>EXAMEN_BLANC : examen blanc CDA chronométré.</li>
 * </ul>
 * Mode ENTRAINEMENT : correction après chaque réponse. Mode EXAMEN : correction à la fin.
 */
@Service
public class QuizService {

    public record StartRequest(String scope, String mode, String course, String chapter, List<String> courses,
            Integer difficulty, Integer count, Integer timeLimitMinutes, String exam) {
    }

    public record ItemView(int position, PublicQuestion question, boolean answered, Answer given, Feedback feedback) {
    }

    public record AttemptView(long id, String scope, String mode, String title, int questionCount,
            Instant startedAt, Instant deadlineAt, Long remainingSeconds, boolean submitted, int passingPercent,
            String courseSlug, String chapterSlug, List<ItemView> items) {
    }

    public record AnswerResult(boolean saved, Feedback feedback) {
    }

    public record ReviewTopic(String theme, String lessonSlug, String lessonTitle, String chapterTitle, int wrong,
            int total) {
    }

    public record ResultItem(int position, PublicQuestion question, Answer given, boolean correct, Feedback feedback) {
    }

    public record ResultView(long id, String scope, String mode, String title, int correct, int total, int percent,
            boolean passed, int passingPercent, int xpEarned, Reward reward, long durationSeconds, Instant submittedAt,
            String courseSlug, String chapterSlug, boolean nextChapterUnlocked, List<ReviewTopic> review,
            List<ResultItem> items, List<CertificateService.CertificateView> newCertificates) {

        ResultView withCertificates(List<CertificateService.CertificateView> certificates) {
            return new ResultView(id, scope, mode, title, correct, total, percent, passed, passingPercent, xpEarned,
                    reward, durationSeconds, submittedAt, courseSlug, chapterSlug, nextChapterUnlocked, review, items,
                    certificates);
        }
    }

    public record HistoryItem(long id, String scope, String mode, String title, int total, Integer percent,
            Boolean passed, Instant startedAt, Instant submittedAt) {
    }

    private static final int GRACE_SECONDS = 30;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final QuestionBank bank;
    private final QuestionStats stats;
    private final CourseAccess access;
    private final ProgressService progress;
    private final CertificateService certificates;

    public QuizService(JdbcTemplate jdbc, ObjectMapper json, QuestionBank bank, QuestionStats stats,
            CourseAccess access, ProgressService progress, CertificateService certificates) {
        this.certificates = certificates;
        this.jdbc = jdbc;
        this.json = json;
        this.bank = bank;
        this.stats = stats;
        this.access = access;
        this.progress = progress;
    }

    // ------------------------------------------------------------------ démarrage

    @Transactional
    public AttemptView start(long userId, boolean admin, StartRequest req) {
        String scope = req.scope() == null ? "ENTRAINEMENT" : req.scope();
        String mode = "EXAMEN".equals(req.mode()) ? "EXAMEN" : "ENTRAINEMENT";
        Long courseId = null;
        Long moduleId = null;
        Long examId = null;
        Integer difficulty = req.difficulty();
        int count;
        Integer timeLimit = null;
        String filter;
        List<Object> args = new ArrayList<>();

        switch (scope) {
            case "CHAPITRE" -> {
                CourseState course = access.load(userId, admin, required(req.course(), "parcours"));
                ChapterState chapter = course.chapters().stream().filter(c -> c.slug().equals(req.chapter()))
                        .findFirst().orElseThrow(() -> new NotFoundException("Chapitre introuvable."));
                if (!chapter.quizAvailable()) {
                    throw new BusinessRuleException(
                            "Le QCM du chapitre s'ouvre quand toutes ses leçons sont terminées.");
                }
                courseId = course.id();
                moduleId = chapter.id();
                mode = "EXAMEN";
                difficulty = null;
                count = chapter.quizQuestionCount();
                filter = "q.module_id = ?";
                args.add(moduleId);
            }
            case "PARCOURS" -> {
                CourseState course = access.load(userId, admin, required(req.course(), "parcours"));
                if (!admin && !course.allChaptersPassed()) {
                    throw new BusinessRuleException(
                            "L'examen final s'ouvre quand tous les QCM de chapitre sont réussis.");
                }
                courseId = course.id();
                mode = "EXAMEN";
                count = clamp(req.count(), 30, 50, course.examQuestionCount());
                timeLimit = req.timeLimitMinutes();
                filter = "q.course_id = ?";
                args.add(courseId);
            }
            case "ENTRAINEMENT" -> {
                count = clamp(req.count(), 5, 50, 10);
                timeLimit = req.timeLimitMinutes();
                StringBuilder f = new StringBuilder("true");
                if (req.course() != null) {
                    courseId = courseId(req.course());
                    f.append(" and q.course_id = ?");
                    args.add(courseId);
                }
                if (req.chapter() != null && courseId != null) {
                    moduleId = jdbc.queryForList("select id from modules where course_id = ? and slug = ?",
                            Long.class, courseId, req.chapter()).stream().findFirst()
                            .orElseThrow(() -> new NotFoundException("Chapitre introuvable."));
                    f.append(" and q.module_id = ?");
                    args.add(moduleId);
                }
                filter = f.toString();
            }
            case "ERREURS" -> {
                count = clamp(req.count(), 5, 50, 15);
                StringBuilder f = new StringBuilder("""
                        exists (select 1 from user_question_stats e where e.question_id = q.id and e.user_id = ?
                                and e.times_wrong > 0 and e.consecutive_correct < %d)"""
                        .formatted(QuestionStats.MASTERY_STREAK));
                args.add(userId);
                if (req.course() != null) {
                    courseId = courseId(req.course());
                    f.append(" and q.course_id = ?");
                    args.add(courseId);
                }
                filter = f.toString();
            }
            case "ALEATOIRE" -> {
                count = clamp(req.count(), 5, 50, 20);
                timeLimit = req.timeLimitMinutes();
                if (req.courses() != null && !req.courses().isEmpty()) {
                    filter = "q.course_id in (select id from courses where slug = any (?))";
                    args.add(req.courses().toArray(String[]::new));
                } else {
                    filter = "q.course_id is not null";
                }
            }
            case "EXAMEN_BLANC" -> {
                List<Map<String, Object>> found = jdbc.queryForList("""
                        select id, duration_minutes, coalesce(question_count, 40) as question_count,
                               array(select jsonb_array_elements_text(content -> 'courses')) as courses
                        from exams where slug = ? and kind = 'EXAMEN_BLANC' and published
                        """, required(req.exam(), "examen"));
                if (found.isEmpty()) {
                    throw new NotFoundException("Examen blanc introuvable.");
                }
                Map<String, Object> exam = found.getFirst();
                examId = ((Number) exam.get("id")).longValue();
                mode = "EXAMEN";
                count = ((Number) exam.get("question_count")).intValue();
                timeLimit = ((Number) exam.get("duration_minutes")).intValue();
                difficulty = null;
                String[] courses = toStrings(exam.get("courses"));
                if (courses.length > 0) {
                    // Examen blanc ciblé : questions des seuls parcours indiqués dans le fichier de l'examen
                    filter = "q.course_id in (select id from courses where slug = any (?))";
                    args.add(courses);
                } else {
                    filter = "q.course_id is not null";
                }
            }
            default -> throw new BusinessRuleException("Type de QCM inconnu : " + scope);
        }
        if (difficulty != null) {
            filter += " and q.difficulty = ?";
            args.add(difficulty);
        }

        List<Long> ids = pickQuestions(userId, scope, filter, args, count);
        if (ids.isEmpty()) {
            throw new BusinessRuleException("ERREURS".equals(scope)
                    ? "Aucune erreur à retravailler pour le moment : bravo !"
                    : "Aucune question ne correspond à ces critères.");
        }
        Integer limitSeconds = timeLimit == null || timeLimit <= 0 ? null : Math.clamp(timeLimit, 1, 240) * 60;
        long attemptId = jdbc.queryForObject("""
                insert into quiz_attempts (user_id, scope, mode, course_id, module_id, exam_id, difficulty,
                                           question_count, time_limit_seconds, deadline_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, case when ?::int is null then null
                                                        else now() + make_interval(secs => ?::int) end)
                returning id
                """, Long.class, userId, scope, mode, courseId, moduleId, examId, difficulty, ids.size(),
                limitSeconds, limitSeconds, limitSeconds);

        Map<Long, Question> questions = bank.byIds(ids);
        for (int i = 0; i < ids.size(); i++) {
            Question q = questions.get(ids.get(i));
            List<Integer> order = new ArrayList<>(q.choices().stream().map(QuestionBank.Choice::position).toList());
            if (!"VRAI_FAUX".equals(q.kind())) {
                Collections.shuffle(order);
            }
            jdbc.update("""
                    insert into quiz_attempt_items (attempt_id, position, question_id, choice_order)
                    values (?, ?, ?, ?)
                    """, attemptId, i + 1, q.id(), order.toArray(Integer[]::new));
        }
        return view(userId, attemptId);
    }

    /**
     * Tirage aléatoire en privilégiant les questions absentes de la tentative précédente
     * du même type et les moins souvent vues : chaque tentative propose de nouvelles questions.
     */
    private List<Long> pickQuestions(long userId, String scope, String filter, List<Object> args, int count) {
        List<Object> all = new ArrayList<>();
        all.add(userId);
        all.add(userId);
        all.add(scope);
        all.addAll(args);
        all.add(count);
        List<Long> ids = new ArrayList<>(jdbc.queryForList("""
                select q.id from questions q
                left join user_question_stats s on s.question_id = q.id and s.user_id = ?
                left join (select i.question_id from quiz_attempt_items i
                           where i.attempt_id = (select max(a.id) from quiz_attempts a
                                                 where a.user_id = ? and a.scope = ?)) last
                       on last.question_id = q.id
                where q.published and q.kind in ('CHOIX_UNIQUE', 'CHOIX_MULTIPLE', 'VRAI_FAUX', 'COMPLETER_CODE',
                                                 'RESULTAT_CODE', 'TEXTE')
                  and\s""" + filter + """

                order by (last.question_id is not null), coalesce(s.times_answered, 0), random()
                limit ?
                """, Long.class, all.toArray()));
        Collections.shuffle(ids);
        return ids;
    }

    // ------------------------------------------------------------------ déroulé

    @Transactional
    public AttemptView view(long userId, long attemptId) {
        Attempt a = attempt(userId, attemptId);
        if (a.submittedAt() == null && a.expired()) {
            submit(userId, attemptId);
            a = attempt(userId, attemptId);
        }
        List<Item> items = items(attemptId);
        Map<Long, Question> questions = bank.byIds(items.stream().map(Item::questionId).toList());
        boolean showFeedback = "ENTRAINEMENT".equals(a.mode()) || a.submittedAt() != null;
        List<ItemView> views = items.stream().map(it -> {
            Question q = questions.get(it.questionId());
            Feedback fb = showFeedback && it.given() != null ? QuestionBank.grade(q, it.given()) : null;
            return new ItemView(it.position(), QuestionBank.toPublic(q, it.choiceOrder()), it.given() != null,
                    it.given(), fb);
        }).toList();
        Long remaining = a.deadlineAt() == null ? null
                : Math.max(0, Duration.between(Instant.now(), a.deadlineAt()).getSeconds());
        return new AttemptView(a.id(), a.scope(), a.mode(), title(a), items.size(), a.startedAt(), a.deadlineAt(),
                remaining, a.submittedAt() != null, passingPercent(a), a.courseSlug(), a.chapterSlug(), views);
    }

    @Transactional
    public AnswerResult answer(long userId, long attemptId, int position, Answer answer) {
        Attempt a = attempt(userId, attemptId);
        if (a.submittedAt() != null) {
            throw new BusinessRuleException("Ce QCM est déjà terminé.");
        }
        if (a.expired()) {
            submit(userId, attemptId);
            throw new BusinessRuleException("Le temps est écoulé : le QCM a été corrigé automatiquement.");
        }
        Item item = items(attemptId).stream().filter(i -> i.position() == position).findFirst()
                .orElseThrow(() -> new NotFoundException("Question introuvable dans ce QCM."));
        if (answer == null || answer.isEmpty()) {
            throw new BusinessRuleException("Choisis ou saisis une réponse avant de valider.");
        }
        boolean training = "ENTRAINEMENT".equals(a.mode());
        if (training && item.given() != null) {
            throw new BusinessRuleException("Tu as déjà répondu à cette question.");
        }
        Question q = bank.byIds(List.of(item.questionId())).get(item.questionId());
        Feedback feedback = QuestionBank.grade(q, answer);
        jdbc.update("""
                update quiz_attempt_items set given_answer = ?::jsonb, correct = ?, answered_at = now()
                where attempt_id = ? and position = ?
                """, toJson(answer), feedback.correct(), attemptId, position);
        if (training) {
            stats.record(userId, q.id(), feedback.correct());
            return new AnswerResult(true, feedback);
        }
        return new AnswerResult(true, null);
    }

    @Transactional
    public ResultView submit(long userId, long attemptId) {
        Attempt a = attempt(userId, attemptId);
        if (a.submittedAt() != null) {
            return result(userId, attemptId, null);
        }
        List<Item> items = items(attemptId);
        Map<Long, Question> questions = bank.byIds(items.stream().map(Item::questionId).toList());
        int correct = 0;
        for (Item it : items) {
            Question q = questions.get(it.questionId());
            boolean ok = it.given() != null && QuestionBank.grade(q, it.given()).correct();
            if (ok) {
                correct++;
            }
            if (!"ENTRAINEMENT".equals(a.mode()) || it.given() == null) {
                stats.record(userId, q.id(), ok);
                jdbc.update("update quiz_attempt_items set correct = ? where attempt_id = ? and position = ?", ok,
                        attemptId, it.position());
            }
        }
        int percent = items.isEmpty() ? 0 : Math.round(100f * correct / items.size());
        boolean passed = percent >= passingPercent(a);
        boolean firstPass = passed && !alreadyPassed(userId, a);
        int xp = Math.min(correct, 30) + (firstPass ? bonus(a.scope()) : 0);
        long seconds = Duration.between(a.startedAt(), Instant.now()).getSeconds();

        jdbc.update("""
                update quiz_attempts set submitted_at = now(), correct_count = ?, percent = ?, passed = ?, xp_earned = ?
                where id = ?
                """, correct, percent, passed, xp, attemptId);
        Reward reward = progress.award(userId, xp, (int) Math.min(seconds, 3 * 3600), false);
        ResultView result = result(userId, attemptId, reward);
        // Un QCM de chapitre, un examen final ou un examen blanc réussi peut ouvrir droit à une attestation
        return passed ? result.withCertificates(certificates.refresh(userId)) : result;
    }

    // ------------------------------------------------------------------ résultats

    @Transactional(readOnly = true)
    public ResultView result(long userId, long attemptId, Reward reward) {
        Attempt a = attempt(userId, attemptId);
        if (a.submittedAt() == null) {
            throw new BusinessRuleException("Ce QCM n'est pas encore terminé.");
        }
        List<Item> items = items(attemptId);
        Map<Long, Question> questions = bank.byIds(items.stream().map(Item::questionId).toList());
        List<ResultItem> results = new ArrayList<>();
        Map<String, int[]> topicCounts = new LinkedHashMap<>();
        Map<String, ReviewTopic> topicInfo = new LinkedHashMap<>();
        Map<Long, String[]> lessonInfo = lessonInfo(questions.values().stream()
                .map(Question::lessonId).filter(Objects::nonNull).distinct().toList());
        for (Item it : items) {
            Question q = questions.get(it.questionId());
            Feedback fb = QuestionBank.grade(q, it.given() == null ? new Answer(List.of(), "") : it.given());
            results.add(new ResultItem(it.position(), QuestionBank.toPublic(q, it.choiceOrder()), it.given(),
                    fb.correct(), fb));

            String[] lesson = q.lessonId() == null ? null : lessonInfo.get(q.lessonId());
            String theme = q.skillName() == null ? "Notions générales" : q.skillName();
            String key = theme + "|" + (lesson == null ? "" : lesson[0]);
            int[] c = topicCounts.computeIfAbsent(key, k -> new int[2]);
            c[1]++;
            if (!fb.correct()) {
                c[0]++;
            }
            topicInfo.putIfAbsent(key, new ReviewTopic(theme, lesson == null ? null : lesson[0],
                    lesson == null ? null : lesson[1], lesson == null ? null : lesson[2], 0, 0));
        }
        List<ReviewTopic> review = topicCounts.entrySet().stream().filter(e -> e.getValue()[0] > 0)
                .map(e -> {
                    ReviewTopic t = topicInfo.get(e.getKey());
                    return new ReviewTopic(t.theme(), t.lessonSlug(), t.lessonTitle(), t.chapterTitle(),
                            e.getValue()[0], e.getValue()[1]);
                })
                .sorted((x, y) -> Integer.compare(y.wrong(), x.wrong()))
                .toList();

        boolean nextUnlocked = false;
        if ("CHAPITRE".equals(a.scope()) && Boolean.TRUE.equals(a.passed())) {
            Integer next = jdbc.queryForObject("""
                    select count(*) from modules m2 join modules m on m.course_id = m2.course_id
                    where m.id = ? and m2.position > m.position
                    """, Integer.class, a.moduleId());
            nextUnlocked = next != null && next > 0;
        }
        return new ResultView(a.id(), a.scope(), a.mode(), title(a), a.correctCount(), items.size(),
                a.percent() == null ? 0 : a.percent(), Boolean.TRUE.equals(a.passed()), passingPercent(a),
                a.xpEarned(), reward, Duration.between(a.startedAt(), a.submittedAt()).getSeconds(), a.submittedAt(),
                a.courseSlug(), a.chapterSlug(), nextUnlocked, review, results, List.of());
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(long userId, String scope, String course) {
        List<Attempt> attempts = jdbc.query(ATTEMPT_SQL + """
                 where a.user_id = ? and (?::text is null or a.scope = ?) and (?::text is null or c.slug = ?)
                 order by a.started_at desc limit 100
                """, this::mapAttempt, userId, scope, scope, course, course);
        return attempts.stream().map(a -> new HistoryItem(a.id(), a.scope(), a.mode(), title(a), a.questionCount(),
                a.percent(), a.passed(), a.startedAt(), a.submittedAt())).toList();
    }

    private static String[] toStrings(Object sqlArray) {
        try {
            return sqlArray instanceof java.sql.Array a ? (String[]) a.getArray() : new String[0];
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ règles

    private int passingPercent(Attempt a) {
        if ("EXAMEN_BLANC".equals(a.scope()) && a.examId() != null) {
            return jdbc.queryForObject("select passing_score from exams where id = ?", Integer.class, a.examId());
        }
        return CourseAccess.PASSING_PERCENT;
    }

    private static int bonus(String scope) {
        return switch (scope) {
            case "CHAPITRE" -> 40;
            case "PARCOURS" -> 150;
            case "EXAMEN_BLANC" -> 100;
            default -> 5;
        };
    }

    private boolean alreadyPassed(long userId, Attempt a) {
        Integer n = jdbc.queryForObject("""
                select count(*) from quiz_attempts where user_id = ? and scope = ? and passed
                  and module_id is not distinct from ? and course_id is not distinct from ?
                  and exam_id is not distinct from ?
                """, Integer.class, userId, a.scope(), a.moduleId(), a.courseId(), a.examId());
        return n != null && n > 0;
    }

    private String title(Attempt a) {
        return switch (a.scope()) {
            case "CHAPITRE" -> "QCM de fin de chapitre — " + a.chapterTitle();
            case "PARCOURS" -> "Examen final — " + a.courseTitle();
            case "ERREURS" -> "Mes erreurs" + (a.courseTitle() == null ? "" : " — " + a.courseTitle());
            case "ALEATOIRE" -> "QCM aléatoire multi-matières";
            case "EXAMEN_BLANC" -> a.examTitle() == null ? "Examen blanc CDA" : a.examTitle();
            default -> "Entraînement — " + (a.chapterTitle() != null ? a.chapterTitle()
                    : a.courseTitle() != null ? a.courseTitle() : "toutes les matières");
        };
    }

    // ------------------------------------------------------------------ accès aux données

    record Attempt(long id, String scope, String mode, Long courseId, Long moduleId, Long examId, int questionCount,
            Instant startedAt, Instant deadlineAt, Instant submittedAt, int correctCount, Integer percent,
            Boolean passed, int xpEarned, String courseSlug, String courseTitle, String chapterSlug,
            String chapterTitle, String examTitle) {

        boolean expired() {
            return deadlineAt != null && Instant.now().isAfter(deadlineAt.plusSeconds(GRACE_SECONDS));
        }
    }

    record Item(int position, long questionId, List<Integer> choiceOrder, Answer given) {
    }

    private static final String ATTEMPT_SQL = """
            select a.id, a.scope, a.mode, a.course_id, a.module_id, a.exam_id, a.question_count, a.started_at,
                   a.deadline_at, a.submitted_at, a.correct_count, a.percent, a.passed, a.xp_earned,
                   c.slug as course_slug, c.title as course_title, m.slug as chapter_slug, m.title as chapter_title,
                   e.title as exam_title
            from quiz_attempts a
            left join courses c on c.id = a.course_id
            left join modules m on m.id = a.module_id
            left join exams e on e.id = a.exam_id
            """;

    private Attempt mapAttempt(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Attempt(rs.getLong("id"), rs.getString("scope"), rs.getString("mode"),
                (Long) rs.getObject("course_id"), (Long) rs.getObject("module_id"), (Long) rs.getObject("exam_id"),
                rs.getInt("question_count"), instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("deadline_at")), instant(rs.getTimestamp("submitted_at")),
                rs.getInt("correct_count"), (Integer) rs.getObject("percent"), (Boolean) rs.getObject("passed"),
                rs.getInt("xp_earned"), rs.getString("course_slug"), rs.getString("course_title"),
                rs.getString("chapter_slug"), rs.getString("chapter_title"), rs.getString("exam_title"));
    }

    private Attempt attempt(long userId, long attemptId) {
        List<Attempt> found = jdbc.query(ATTEMPT_SQL + " where a.id = ? and a.user_id = ?", this::mapAttempt,
                attemptId, userId);
        if (found.isEmpty()) {
            throw new NotFoundException("QCM introuvable.");
        }
        return found.getFirst();
    }

    private List<Item> items(long attemptId) {
        return jdbc.query("""
                select position, question_id, choice_order, given_answer from quiz_attempt_items
                where attempt_id = ? order by position
                """, (rs, i) -> {
            Integer[] order = (Integer[]) rs.getArray("choice_order").getArray();
            String given = rs.getString("given_answer");
            return new Item(rs.getInt("position"), rs.getLong("question_id"), List.of(order),
                    given == null ? null : fromJson(given));
        }, attemptId);
    }

    /** Leçon, titre et chapitre associés à chaque question (pour le résumé des notions à revoir). */
    private Map<Long, String[]> lessonInfo(List<Long> lessonIds) {
        Map<Long, String[]> info = new LinkedHashMap<>();
        if (lessonIds.isEmpty()) {
            return info;
        }
        jdbc.query("""
                select l.id, l.slug, l.title, m.title from lessons l join modules m on m.id = l.module_id
                where l.id = any (?)
                """, rs -> {
            info.put(rs.getLong(1), new String[] {rs.getString(2), rs.getString(3), rs.getString(4)});
        }, (Object) lessonIds.toArray(Long[]::new));
        return info;
    }

    private long courseId(String slug) {
        List<Long> ids = jdbc.queryForList("select id from courses where slug = ?", Long.class, slug);
        if (ids.isEmpty()) {
            throw new NotFoundException("Parcours introuvable.");
        }
        return ids.getFirst();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        return Math.clamp(value == null ? fallback : value, min, max);
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("Précise le " + what + ".");
        }
        return value;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Answer fromJson(String s) {
        try {
            JsonNode node = json.readTree(s);
            return json.treeToValue(node, Answer.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
