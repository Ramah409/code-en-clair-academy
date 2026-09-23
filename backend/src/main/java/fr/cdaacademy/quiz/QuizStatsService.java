package fr.cdaacademy.quiz;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tableau de bord de l'entraînement QCM : options, statistiques par thème et erreurs récurrentes. */
@Service
public class QuizStatsService {

    public record ChapterOption(String slug, String title, String level, int questionCount) {
    }

    public record CourseOption(String slug, String title, String icon, int questionCount, List<Integer> difficulties,
            List<ChapterOption> chapters) {
    }

    public record ExamOption(String slug, String title, String description, int durationMinutes, int questionCount,
            int passingScore, Integer bestPercent, int attempts) {
    }

    public record Options(List<CourseOption> courses, List<ExamOption> exams, int mistakesToReview) {
    }

    public record ThemeStat(String theme, String course, int answered, int correct, int rate) {
    }

    public record RecurringError(String code, String prompt, String course, String theme, int timesWrong,
            int timesCorrect, Instant lastWrongAt, boolean fixed) {
    }

    public record Stats(int answered, int correct, int rate, int quizzesDone, int quizzesPassed,
            int mistakesToReview, List<ThemeStat> themes, List<RecurringError> recurringErrors) {
    }

    private final JdbcTemplate jdbc;

    public QuizStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Options options(long userId) {
        Map<String, List<ChapterOption>> chapters = new LinkedHashMap<>();
        jdbc.query("""
                select c.slug, m.slug, m.title, m.level,
                       (select count(*) from questions q where q.module_id = m.id and q.published) as n
                from modules m join courses c on c.id = m.course_id where c.published order by c.level_number, m.position
                """, rs -> {
            chapters.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                    .add(new ChapterOption(rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
        });
        List<CourseOption> courses = jdbc.query("""
                select c.slug, c.title, c.icon,
                       (select count(*) from questions q where q.course_id = c.id and q.published) as n,
                       (select array_agg(distinct q.difficulty order by q.difficulty) from questions q
                         where q.course_id = c.id and q.published) as difficulties
                from courses c where c.published order by c.level_number
                """, (rs, i) -> {
            java.sql.Array arr = rs.getArray("difficulties");
            List<Integer> diffs = new ArrayList<>();
            if (arr != null) {
                for (Object o : (Object[]) arr.getArray()) {
                    diffs.add(((Number) o).intValue());
                }
            }
            return new CourseOption(rs.getString("slug"), rs.getString("title"), rs.getString("icon"),
                    rs.getInt("n"), diffs, chapters.getOrDefault(rs.getString("slug"), List.of()));
        });
        List<ExamOption> exams = jdbc.query("""
                select e.slug, e.title, e.description, e.duration_minutes, coalesce(e.question_count, 40) as n,
                       e.passing_score,
                       (select max(percent) from quiz_attempts a where a.exam_id = e.id and a.user_id = ?
                          and a.submitted_at is not null) as best,
                       (select count(*) from quiz_attempts a where a.exam_id = e.id and a.user_id = ?
                          and a.submitted_at is not null) as attempts
                from exams e where e.kind = 'EXAMEN_BLANC' and e.published order by e.id
                """, (rs, i) -> new ExamOption(rs.getString("slug"), rs.getString("title"),
                rs.getString("description"), rs.getInt("duration_minutes"), rs.getInt("n"),
                rs.getInt("passing_score"), (Integer) rs.getObject("best"), rs.getInt("attempts")), userId, userId);
        return new Options(courses, exams, mistakes(userId));
    }

    @Transactional(readOnly = true)
    public Stats stats(long userId) {
        List<ThemeStat> themes = jdbc.query("""
                select coalesce(sk.name, 'Notions générales') as theme, coalesce(c.title, '—') as course,
                       sum(s.times_answered) as answered, sum(s.times_correct) as correct
                from user_question_stats s
                join questions q on q.id = s.question_id
                left join skills sk on sk.id = q.skill_id
                left join courses c on c.id = q.course_id
                where s.user_id = ?
                group by 1, 2 order by sum(s.times_answered) desc
                """, (rs, i) -> {
            int answered = rs.getInt("answered");
            int correct = rs.getInt("correct");
            return new ThemeStat(rs.getString("theme"), rs.getString("course"), answered, correct,
                    answered == 0 ? 0 : Math.round(100f * correct / answered));
        }, userId);
        List<RecurringError> errors = jdbc.query("""
                select q.code, q.prompt, c.title as course, coalesce(sk.name, 'Notions générales') as theme,
                       s.times_wrong, s.times_correct, s.last_wrong_at, s.consecutive_correct >= ? as fixed
                from user_question_stats s
                join questions q on q.id = s.question_id
                left join skills sk on sk.id = q.skill_id
                left join courses c on c.id = q.course_id
                where s.user_id = ? and s.times_wrong > 0
                order by s.times_wrong desc, s.last_wrong_at desc limit 15
                """, (rs, i) -> {
            Timestamp t = rs.getTimestamp("last_wrong_at");
            return new RecurringError(rs.getString("code"), rs.getString("prompt"), rs.getString("course"),
                    rs.getString("theme"), rs.getInt("times_wrong"), rs.getInt("times_correct"),
                    t == null ? null : t.toInstant(), rs.getBoolean("fixed"));
        }, QuestionStats.MASTERY_STREAK, userId);
        Map<String, Object> totals = jdbc.queryForMap("""
                select coalesce(sum(times_answered), 0) as answered, coalesce(sum(times_correct), 0) as correct,
                       (select count(*) from quiz_attempts where user_id = ? and submitted_at is not null) as done,
                       (select count(*) from quiz_attempts where user_id = ? and passed) as passed
                from user_question_stats where user_id = ?
                """, userId, userId, userId);
        int answered = ((Number) totals.get("answered")).intValue();
        int correct = ((Number) totals.get("correct")).intValue();
        return new Stats(answered, correct, answered == 0 ? 0 : Math.round(100f * correct / answered),
                ((Number) totals.get("done")).intValue(), ((Number) totals.get("passed")).intValue(),
                mistakes(userId), themes, errors);
    }

    int mistakes(long userId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from user_question_stats s join questions q on q.id = s.question_id
                where s.user_id = ? and s.times_wrong > 0 and s.consecutive_correct < ? and q.published
                """, Integer.class, userId, QuestionStats.MASTERY_STREAK);
        return n == null ? 0 : n;
    }
}
