package fr.cdaacademy.course;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import fr.cdaacademy.common.NotFoundException;

/**
 * Calcule l'état d'un parcours pour une apprenante : leçons terminées, chapitres débloqués,
 * disponibilité des QCM de chapitre, de l'examen final et du projet.
 *
 * Règles de déblocage (validation obligatoire avant la suite) :
 * <ul>
 *   <li>la première leçon d'un chapitre débloqué est accessible ;</li>
 *   <li>une leçon se débloque quand la précédente est terminée ;</li>
 *   <li>le QCM de fin de chapitre s'ouvre quand toutes ses leçons sont terminées ;</li>
 *   <li>le chapitre suivant se débloque quand ce QCM est réussi (80 % ou plus) ;</li>
 *   <li>l'examen final et le projet s'ouvrent quand tous les chapitres sont validés.</li>
 * </ul>
 * Une administratrice a accès à tout, pour relire les contenus.
 */
@Component
public class CourseAccess {

    public static final int PASSING_PERCENT = 80;

    public enum LessonStatus { LOCKED, AVAILABLE, IN_PROGRESS, DONE }

    public record LessonState(long id, String slug, String title, int position, int duration, int xp,
            LessonStatus status) {
    }

    public record ChapterState(long id, String slug, String title, String summary, String level, int position,
            int quizQuestionCount, int bankSize, boolean unlocked, boolean lessonsDone, boolean quizPassed,
            Integer bestQuizPercent, int quizAttempts, List<LessonState> lessons) {

        public boolean quizAvailable() {
            return unlocked && lessonsDone;
        }
    }

    public record CourseState(long id, String slug, String title, String summary, String description,
            String category, String icon, int examQuestionCount, Integer examTimeLimitMinutes,
            List<ChapterState> chapters, boolean examPassed, Integer bestExamPercent, int examAttempts) {

        public boolean allChaptersPassed() {
            return !chapters.isEmpty() && chapters.stream().allMatch(ChapterState::quizPassed);
        }

        public int lessonCount() {
            return chapters.stream().mapToInt(c -> c.lessons().size()).sum();
        }

        public int lessonsDone() {
            return (int) chapters.stream().flatMap(c -> c.lessons().stream())
                    .filter(l -> l.status() == LessonStatus.DONE).count();
        }
    }

    private final JdbcTemplate jdbc;

    public CourseAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CourseState load(long userId, boolean admin, String courseSlug) {
        List<CourseState> found = jdbc.query("""
                select c.id, c.slug, c.title, c.summary, c.description, c.category, c.icon, c.exam_question_count,
                       c.exam_time_limit_minutes,
                       exists (select 1 from quiz_attempts a where a.user_id = ? and a.course_id = c.id
                               and a.scope = 'PARCOURS' and a.passed) as exam_passed,
                       (select max(percent) from quiz_attempts a where a.user_id = ? and a.course_id = c.id
                               and a.scope = 'PARCOURS' and a.submitted_at is not null) as best_exam,
                       (select count(*) from quiz_attempts a where a.user_id = ? and a.course_id = c.id
                               and a.scope = 'PARCOURS' and a.submitted_at is not null) as exam_attempts
                from courses c where c.slug = ? and c.published
                """, (rs, i) -> new CourseState(rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("description"), rs.getString("category"), rs.getString("icon"),
                rs.getInt("exam_question_count"), (Integer) rs.getObject("exam_time_limit_minutes"), null,
                rs.getBoolean("exam_passed"), (Integer) rs.getObject("best_exam"), rs.getInt("exam_attempts")),
                userId, userId, userId, courseSlug);
        if (found.isEmpty()) {
            throw new NotFoundException("Parcours introuvable.");
        }
        CourseState c = found.getFirst();
        return new CourseState(c.id(), c.slug(), c.title(), c.summary(), c.description(), c.category(), c.icon(),
                c.examQuestionCount(), c.examTimeLimitMinutes(), chapters(userId, admin, c.id()), c.examPassed(),
                c.bestExamPercent(), c.examAttempts());
    }

    public CourseState loadByLesson(long userId, boolean admin, String lessonSlug) {
        List<String> slugs = jdbc.queryForList("""
                select c.slug from lessons l join modules m on m.id = l.module_id join courses c on c.id = m.course_id
                where l.slug = ? and l.published
                """, String.class, lessonSlug);
        if (slugs.isEmpty()) {
            throw new NotFoundException("Leçon introuvable.");
        }
        return load(userId, admin, slugs.getFirst());
    }

    public CourseState loadByChapter(long userId, boolean admin, long moduleId) {
        String slug = jdbc.queryForObject(
                "select c.slug from modules m join courses c on c.id = m.course_id where m.id = ?", String.class,
                moduleId);
        return load(userId, admin, slug);
    }

    private List<ChapterState> chapters(long userId, boolean admin, long courseId) {
        record Raw(long id, String slug, String title, String summary, String level, int position, int quizSize,
                int bank, boolean passed, Integer best, int attempts) {
        }
        List<Raw> raws = jdbc.query("""
                select m.id, m.slug, m.title, m.summary, m.level, m.position, m.quiz_question_count,
                       (select count(*) from questions q where q.module_id = m.id and q.published) as bank,
                       exists (select 1 from quiz_attempts a where a.user_id = ? and a.module_id = m.id
                               and a.scope = 'CHAPITRE' and a.passed) as passed,
                       (select max(percent) from quiz_attempts a where a.user_id = ? and a.module_id = m.id
                               and a.scope = 'CHAPITRE' and a.submitted_at is not null) as best,
                       (select count(*) from quiz_attempts a where a.user_id = ? and a.module_id = m.id
                               and a.scope = 'CHAPITRE' and a.submitted_at is not null) as attempts
                from modules m where m.course_id = ? order by m.position
                """, (rs, i) -> new Raw(rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("level"), rs.getInt("position"),
                rs.getInt("quiz_question_count"), rs.getInt("bank"), rs.getBoolean("passed"),
                (Integer) rs.getObject("best"), rs.getInt("attempts")), userId, userId, userId, courseId);

        record RawLesson(long id, String slug, String title, int position, int duration, int xp, String status) {
        }
        Map<Long, List<RawLesson>> lessons = new LinkedHashMap<>();
        jdbc.query("""
                select l.module_id, l.id, l.slug, l.title, l.position, l.duration_minutes, l.xp_reward, p.status
                from lessons l join modules m on m.id = l.module_id
                left join progress p on p.lesson_id = l.id and p.user_id = ?
                where m.course_id = ? and l.published order by m.position, l.position
                """, rs -> {
            lessons.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new RawLesson(rs.getLong(2),
                    rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8)));
        }, userId, courseId);

        List<ChapterState> result = new ArrayList<>();
        boolean previousPassed = true;
        for (Raw r : raws) {
            boolean unlocked = admin || previousPassed;
            List<LessonState> states = new ArrayList<>();
            boolean previousDone = true;
            for (RawLesson l : lessons.getOrDefault(r.id(), List.of())) {
                LessonStatus status;
                if ("TERMINEE".equals(l.status())) {
                    status = LessonStatus.DONE;
                } else if (!(unlocked && (admin || previousDone))) {
                    status = LessonStatus.LOCKED;
                } else if ("EN_COURS".equals(l.status())) {
                    status = LessonStatus.IN_PROGRESS;
                } else {
                    status = LessonStatus.AVAILABLE;
                }
                states.add(new LessonState(l.id(), l.slug(), l.title(), l.position(), l.duration(), l.xp(), status));
                previousDone = status == LessonStatus.DONE;
            }
            boolean lessonsDone = states.stream().allMatch(s -> s.status() == LessonStatus.DONE);
            result.add(new ChapterState(r.id(), r.slug(), r.title(), r.summary(), r.level(), r.position(),
                    r.quizSize(), r.bank(), unlocked, admin || lessonsDone, r.passed(), r.best(), r.attempts(),
                    states));
            previousPassed = r.passed();
        }
        return result;
    }
}
