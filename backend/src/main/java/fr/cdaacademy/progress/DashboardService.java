package fr.cdaacademy.progress;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.cdaacademy.course.CourseService;
import fr.cdaacademy.course.CourseService.CourseSummary;
import fr.cdaacademy.user.LevelCalculator;

/** Données de l'accueil et de la page Progression. */
@Service
public class DashboardService {

    public record LevelInfo(int xp, int level, int levelStartXp, int nextLevelXp, int percent) {
    }

    public record DailyGoal(int goalMinutes, int minutesToday, int xpToday, int percent, boolean reached) {
    }

    public record Streak(int current, int longest, boolean activeToday) {
    }

    public record Resume(String lessonSlug, String lessonTitle, String chapterTitle, String courseSlug,
            String courseTitle, int currentStep, boolean started) {
    }

    public record DayActivity(LocalDate date, int minutes, int xp, int exercises) {
    }

    public record BadgeView(String code, String name, String description, String icon, String criteria, int threshold,
            long value, Instant earnedAt) {
    }

    public record Totals(int lessonsDone, int exercisesSolved, int quizzesPassed, int chaptersPassed,
            int questionsAnswered, int minutesStudied) {
    }

    public record Dashboard(String displayName, LevelInfo level, DailyGoal dailyGoal, Streak streak, Resume resume,
            List<CourseSummary> courses, int mistakesToReview, List<BadgeView> recentBadges,
            List<DayActivity> lastDays, Totals totals) {
    }

    public record Progress(LevelInfo level, DailyGoal dailyGoal, Streak streak, Totals totals,
            List<DayActivity> activity, List<BadgeView> badges, List<CourseSummary> courses) {
    }

    private final JdbcTemplate jdbc;
    private final CourseService courses;

    public DashboardService(JdbcTemplate jdbc, CourseService courses) {
        this.jdbc = jdbc;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(long userId, boolean admin) {
        Map<String, Object> u = user(userId);
        List<CourseSummary> catalog = courses.catalog(userId, admin);
        List<BadgeView> earned = badges(userId).stream().filter(b -> b.earnedAt() != null)
                .sorted((a, b) -> b.earnedAt().compareTo(a.earnedAt())).limit(4).toList();
        Integer mistakes = jdbc.queryForObject("""
                select count(*) from user_question_stats where user_id = ? and times_wrong > 0
                  and consecutive_correct < 2
                """, Integer.class, userId);
        return new Dashboard((String) u.get("display_name"), level(u), goal(userId, u), streak(u),
                resume(userId, catalog), catalog, mistakes == null ? 0 : mistakes, earned, activity(userId, 7),
                totals(userId));
    }

    @Transactional(readOnly = true)
    public Progress progress(long userId, boolean admin) {
        Map<String, Object> u = user(userId);
        return new Progress(level(u), goal(userId, u), streak(u), totals(userId), activity(userId, 120),
                badges(userId), courses.catalog(userId, admin));
    }

    // ------------------------------------------------------------------ calculs

    private Map<String, Object> user(long userId) {
        return jdbc.queryForMap("""
                select display_name, xp, current_streak, longest_streak, last_activity_date, daily_goal_minutes
                from users where id = ?
                """, userId);
    }

    private static LevelInfo level(Map<String, Object> u) {
        int xp = ((Number) u.get("xp")).intValue();
        int level = LevelCalculator.level(xp);
        int start = LevelCalculator.xpForLevel(level);
        int next = LevelCalculator.xpForLevel(level + 1);
        return new LevelInfo(xp, level, start, next, Math.round(100f * (xp - start) / (next - start)));
    }

    private static Streak streak(Map<String, Object> u) {
        LocalDate last = u.get("last_activity_date") == null ? null
                : ((Date) u.get("last_activity_date")).toLocalDate();
        int current = ProgressService.effectiveStreak(((Number) u.get("current_streak")).intValue(), last);
        return new Streak(current, ((Number) u.get("longest_streak")).intValue(),
                ProgressService.today().equals(last));
    }

    private DailyGoal goal(long userId, Map<String, Object> u) {
        int goal = ((Number) u.get("daily_goal_minutes")).intValue();
        Map<String, Object> today = jdbc.query("""
                select seconds_studied, xp_earned from daily_activity where user_id = ? and activity_date = ?
                """, rs -> rs.next() ? Map.<String, Object>of("s", rs.getInt(1), "x", rs.getInt(2)) : Map.of(),
                userId, ProgressService.today());
        int minutes = today.isEmpty() ? 0 : ((Integer) today.get("s")) / 60;
        int xp = today.isEmpty() ? 0 : (Integer) today.get("x");
        return new DailyGoal(goal, minutes, xp, Math.min(100, Math.round(100f * minutes / goal)), minutes >= goal);
    }

    /** Dernière leçon ouverte et non terminée, sinon prochaine leçon du parcours le plus avancé. */
    private Resume resume(long userId, List<CourseSummary> catalog) {
        List<Resume> inProgress = jdbc.query("""
                select l.slug, l.title, m.title, c.slug, c.title, p.current_step
                from progress p join lessons l on l.id = p.lesson_id join modules m on m.id = l.module_id
                join courses c on c.id = m.course_id
                where p.user_id = ? and p.status = 'EN_COURS' and l.published
                order by p.updated_at desc limit 1
                """, (rs, i) -> new Resume(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getInt(6), true), userId);
        if (!inProgress.isEmpty()) {
            return inProgress.getFirst();
        }
        return catalog.stream().filter(c -> c.nextLesson() != null)
                .sorted((a, b) -> Integer.compare(b.lessonsDone(), a.lessonsDone()))
                .findFirst()
                .map(c -> new Resume(c.nextLesson().slug(), c.nextLesson().title(), c.nextLesson().chapterTitle(),
                        c.slug(), c.title(), 0, c.lessonsDone() > 0))
                .orElse(null);
    }

    private List<DayActivity> activity(long userId, int days) {
        LocalDate from = ProgressService.today().minusDays(days - 1L);
        Map<LocalDate, DayActivity> byDate = new HashMap<>();
        jdbc.query("""
                select activity_date, seconds_studied, xp_earned, exercises_done from daily_activity
                where user_id = ? and activity_date >= ?
                """, rs -> {
            LocalDate d = rs.getDate(1).toLocalDate();
            byDate.put(d, new DayActivity(d, rs.getInt(2) / 60, rs.getInt(3), rs.getInt(4)));
        }, userId, from);
        List<DayActivity> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            result.add(byDate.getOrDefault(d, new DayActivity(d, 0, 0, 0)));
        }
        return result;
    }

    private Totals totals(long userId) {
        Map<String, Object> t = jdbc.queryForMap("""
                select
                  (select count(*) from progress where user_id = ? and status = 'TERMINEE') as lessons,
                  (select count(*) from exercise_states where user_id = ? and solved_at is not null) as exercises,
                  (select count(*) from quiz_attempts where user_id = ? and passed) as quizzes,
                  (select count(distinct module_id) from quiz_attempts where user_id = ? and scope = 'CHAPITRE'
                     and passed) as chapters,
                  (select coalesce(sum(times_answered), 0) from user_question_stats where user_id = ?) as answered,
                  (select coalesce(sum(seconds_studied), 0) from daily_activity where user_id = ?) as seconds
                """, userId, userId, userId, userId, userId, userId);
        return new Totals(num(t, "lessons"), num(t, "exercises"), num(t, "quizzes"), num(t, "chapters"),
                num(t, "answered"), num(t, "seconds") / 60);
    }

    /** Tous les badges, avec la valeur atteinte pour afficher la progression vers les suivants. */
    private List<BadgeView> badges(long userId) {
        Map<String, Object> m = jdbc.queryForMap("""
                select
                  (select count(*) from progress where user_id = u.id and status = 'TERMINEE') as "LECONS_TERMINEES",
                  u.xp as "XP_TOTAL", u.longest_streak as "SERIE_JOURS",
                  (select count(*) from exercise_states where user_id = u.id and solved_at is not null) as "EXERCICES_REUSSIS",
                  (select count(*) from exercise_states s join exercises e on e.id = s.exercise_id
                     where s.user_id = u.id and s.solved_at is not null and e.kind = 'SQL') as "REQUETES_SQL",
                  (select count(*) from mcd_diagrams where user_id = u.id) as "MCD_CREES",
                  (select count(*) from quiz_attempts where user_id = u.id and scope = 'EXAMEN_BLANC' and passed) as "EXAMENS_REUSSIS",
                  0 as "PROJETS_TERMINES",
                  (select count(*) from quiz_attempt_items i join quiz_attempts a on a.id = i.attempt_id
                     where a.user_id = u.id and a.scope = 'ERREURS' and i.correct is not null) as "REVISIONS",
                  (select count(*) from quiz_attempts where user_id = u.id and passed) as "QUIZ_REUSSIS",
                  (select count(distinct module_id) from quiz_attempts where user_id = u.id and scope = 'CHAPITRE'
                     and passed) as "CHAPITRES_VALIDES",
                  (select count(distinct course_id) from quiz_attempts where user_id = u.id and scope = 'PARCOURS'
                     and passed) as "PARCOURS_TERMINES"
                from users u where u.id = ?
                """, userId);
        return jdbc.query("""
                select b.code, b.name, b.description, b.icon, b.criteria_type, b.threshold, ub.earned_at
                from badges b left join user_badges ub on ub.badge_id = b.id and ub.user_id = ?
                order by ub.earned_at is null, b.threshold, b.id
                """, (rs, i) -> {
            Timestamp earned = rs.getTimestamp("earned_at");
            Object value = m.get(rs.getString("criteria_type"));
            return new BadgeView(rs.getString("code"), rs.getString("name"), rs.getString("description"),
                    rs.getString("icon"), rs.getString("criteria_type"), rs.getInt("threshold"),
                    value == null ? 0 : ((Number) value).longValue(), earned == null ? null : earned.toInstant());
        }, userId);
    }

    private static int num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).intValue();
    }
}
