package fr.cdaacademy.course;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.cdaacademy.course.CourseAccess.ChapterState;
import fr.cdaacademy.course.CourseAccess.CourseState;
import fr.cdaacademy.course.CourseAccess.LessonState;
import fr.cdaacademy.course.CourseAccess.LessonStatus;

/** Catalogue des parcours et détail d'un parcours (chapitres, leçons, QCM, examen, projet). */
@Service
public class CourseService {

    public record NextLesson(String slug, String title, String chapterTitle) {
    }

    public record CourseSummary(String slug, String title, String summary, String category, String icon,
            int chapterCount, int lessonCount, int lessonsDone, int percent, List<String> levels, int questionCount,
            int exerciseCount, boolean examPassed, NextLesson nextLesson) {
    }

    public record ProjectSummary(String slug, String title, String difficulty, int stepCount, int stepsDone,
            boolean available) {
    }

    public record CourseDetail(String slug, String title, String summary, String description, String category,
            String icon, int lessonCount, int lessonsDone, int percent, List<ChapterState> chapters,
            int passingPercent, int examQuestionCount, Integer examTimeLimitMinutes, boolean examAvailable,
            boolean examPassed, Integer bestExamPercent, int examAttempts, int questionCount,
            ProjectSummary project, NextLesson nextLesson) {
    }

    private final JdbcTemplate jdbc;
    private final CourseAccess access;

    public CourseService(JdbcTemplate jdbc, CourseAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<CourseSummary> catalog(long userId, boolean admin) {
        List<String> slugs = jdbc.queryForList("select slug from courses where published order by level_number",
                String.class);
        return slugs.stream().map(slug -> summary(access.load(userId, admin, slug))).toList();
    }

    private CourseSummary summary(CourseState c) {
        List<String> levels = c.chapters().stream().map(ChapterState::level).distinct().toList();
        Integer questions = jdbc.queryForObject(
                "select count(*) from questions where course_id = ? and published", Integer.class, c.id());
        Integer exercises = jdbc.queryForObject(
                "select count(*) from exercises where course_id = ? and published", Integer.class, c.id());
        return new CourseSummary(c.slug(), c.title(), c.summary(), c.category(), c.icon(), c.chapters().size(),
                c.lessonCount(), c.lessonsDone(), percent(c), levels, questions == null ? 0 : questions,
                exercises == null ? 0 : exercises, c.examPassed(), next(c));
    }

    @Transactional(readOnly = true)
    public CourseDetail detail(long userId, boolean admin, String slug) {
        CourseState c = access.load(userId, admin, slug);
        Integer questions = jdbc.queryForObject(
                "select count(*) from questions where course_id = ? and published", Integer.class, c.id());
        boolean finished = admin || c.allChaptersPassed();
        List<ProjectSummary> projects = jdbc.query("""
                select p.slug, p.title, p.difficulty,
                       (select count(*) from project_steps s where s.project_id = p.id) as steps,
                       (select count(*) from project_steps s join user_project_steps u on u.step_id = s.id
                         where s.project_id = p.id and u.user_id = ?) as done
                from projects p where p.course_id = ?
                """, (rs, i) -> new ProjectSummary(rs.getString("slug"), rs.getString("title"),
                rs.getString("difficulty"), rs.getInt("steps"), rs.getInt("done"), finished), userId, c.id());
        return new CourseDetail(c.slug(), c.title(), c.summary(), c.description(), c.category(), c.icon(),
                c.lessonCount(), c.lessonsDone(), percent(c), c.chapters(), CourseAccess.PASSING_PERCENT,
                c.examQuestionCount(), c.examTimeLimitMinutes(), finished, c.examPassed(), c.bestExamPercent(),
                c.examAttempts(), questions == null ? 0 : questions, projects.isEmpty() ? null : projects.getFirst(),
                next(c));
    }

    static int percent(CourseState c) {
        return c.lessonCount() == 0 ? 0 : Math.round(100f * c.lessonsDone() / c.lessonCount());
    }

    /** Prochaine leçon à suivre : en cours ou première leçon disponible. */
    static NextLesson next(CourseState c) {
        for (ChapterState ch : c.chapters()) {
            for (LessonState l : ch.lessons()) {
                if (l.status() == LessonStatus.IN_PROGRESS || l.status() == LessonStatus.AVAILABLE) {
                    return new NextLesson(l.slug(), l.title(), ch.title());
                }
            }
        }
        return null;
    }
}
