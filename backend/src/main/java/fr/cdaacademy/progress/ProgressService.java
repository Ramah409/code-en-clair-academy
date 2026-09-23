package fr.cdaacademy.progress;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fr.cdaacademy.user.LevelCalculator;

/**
 * Récompenses et progression : points d'expérience, niveaux, séries de jours,
 * temps d'étude quotidien et badges.
 */
@Service
public class ProgressService {

    /** Les journées d'étude sont comptées à l'heure française. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    /** Temps maximal comptabilisé pour une seule activité (évite les onglets oubliés). */
    static final int MAX_SECONDS_PER_ACTIVITY = 45 * 60;

    public record Badge(String code, String name, String description, String icon) {
    }

    public record Reward(int xpEarned, int totalXp, int level, boolean levelUp, int streak, List<Badge> newBadges) {
        public static Reward none() {
            return new Reward(0, 0, 0, false, 0, List.of());
        }
    }

    private final JdbcTemplate jdbc;

    public ProgressService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Enregistre une activité : ajoute l'XP, met à jour la série de jours et le temps d'étude
     * du jour, puis attribue les badges nouvellement mérités.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Reward award(long userId, int xp, int seconds, boolean exerciseDone) {
        Map<String, Object> user = jdbc.queryForMap(
                "select xp, current_streak, longest_streak, last_activity_date from users where id = ? for update",
                userId);
        int oldXp = ((Number) user.get("xp")).intValue();
        int streak = ((Number) user.get("current_streak")).intValue();
        int longest = ((Number) user.get("longest_streak")).intValue();
        LocalDate last = user.get("last_activity_date") == null ? null
                : ((Date) user.get("last_activity_date")).toLocalDate();

        LocalDate today = today();
        if (last == null || last.isBefore(today.minusDays(1))) {
            streak = 1;
        } else if (last.equals(today.minusDays(1))) {
            streak++;
        }
        longest = Math.max(longest, streak);
        int gained = Math.max(xp, 0);
        int newXp = oldXp + gained;

        jdbc.update("""
                update users set xp = ?, current_streak = ?, longest_streak = ?, last_activity_date = ?,
                                 updated_at = now()
                where id = ?
                """, newXp, streak, longest, today, userId);
        jdbc.update("""
                insert into daily_activity (user_id, activity_date, seconds_studied, xp_earned, exercises_done)
                values (?, ?, ?, ?, ?)
                on conflict (user_id, activity_date) do update set
                    seconds_studied = daily_activity.seconds_studied + excluded.seconds_studied,
                    xp_earned = daily_activity.xp_earned + excluded.xp_earned,
                    exercises_done = daily_activity.exercises_done + excluded.exercises_done
                """, userId, today, Math.clamp(seconds, 0, MAX_SECONDS_PER_ACTIVITY), gained, exerciseDone ? 1 : 0);

        List<Badge> badges = grantBadges(userId);
        return new Reward(gained, newXp, LevelCalculator.level(newXp),
                LevelCalculator.level(newXp) > LevelCalculator.level(oldXp), streak, badges);
    }

    /** Série affichée : elle retombe à 0 si la dernière activité date d'avant-hier ou plus. */
    public static int effectiveStreak(int storedStreak, LocalDate lastActivity) {
        if (lastActivity == null || lastActivity.isBefore(today().minusDays(1))) {
            return 0;
        }
        return storedStreak;
    }

    private List<Badge> grantBadges(long userId) {
        Map<String, Object> m = jdbc.queryForMap("""
                select
                  (select count(*) from progress where user_id = u.id and status = 'TERMINEE') as lecons,
                  u.xp as xp,
                  u.longest_streak as serie,
                  (select count(*) from exercise_states where user_id = u.id and solved_at is not null) as exercices,
                  (select count(*) from exercise_states s join exercises e on e.id = s.exercise_id
                     where s.user_id = u.id and s.solved_at is not null and e.kind = 'SQL') as sql,
                  (select count(*) from mcd_diagrams where user_id = u.id)
                    + (select count(*) from exercise_states s join exercises e on e.id = s.exercise_id
                         where s.user_id = u.id and s.solved_at is not null and e.kind = 'MCD') as mcd,
                  (select count(*) from quiz_attempts where user_id = u.id and scope = 'EXAMEN_BLANC' and passed) as examens,
                  (select count(*) from projects p where exists (select 1 from project_steps ps where ps.project_id = p.id)
                     and not exists (select 1 from project_steps ps where ps.project_id = p.id
                       and not exists (select 1 from user_project_steps ups
                                        where ups.step_id = ps.id and ups.user_id = u.id))) as projets,
                  (select count(*) from quiz_attempt_items i join quiz_attempts a on a.id = i.attempt_id
                     where a.user_id = u.id and a.scope = 'ERREURS' and i.correct is not null) as revisions,
                  (select count(*) from quiz_attempts where user_id = u.id and passed) as quiz,
                  (select count(distinct module_id) from quiz_attempts
                     where user_id = u.id and scope = 'CHAPITRE' and passed) as chapitres,
                  (select count(distinct course_id) from quiz_attempts
                     where user_id = u.id and scope = 'PARCOURS' and passed) as parcours
                from users u where u.id = ?
                """, userId);
        Map<String, String> metricByType = Map.ofEntries(
                Map.entry("LECONS_TERMINEES", "lecons"), Map.entry("XP_TOTAL", "xp"),
                Map.entry("SERIE_JOURS", "serie"), Map.entry("EXERCICES_REUSSIS", "exercices"),
                Map.entry("REQUETES_SQL", "sql"), Map.entry("MCD_CREES", "mcd"),
                Map.entry("EXAMENS_REUSSIS", "examens"), Map.entry("PROJETS_TERMINES", "projets"),
                Map.entry("REVISIONS", "revisions"), Map.entry("QUIZ_REUSSIS", "quiz"),
                Map.entry("CHAPITRES_VALIDES", "chapitres"), Map.entry("PARCOURS_TERMINES", "parcours"));

        List<Badge> granted = new ArrayList<>();
        jdbc.query("""
                select b.id, b.code, b.name, b.description, b.icon, b.criteria_type, b.threshold from badges b
                where not exists (select 1 from user_badges ub where ub.badge_id = b.id and ub.user_id = ?)
                """, rs -> {
            String metric = metricByType.get(rs.getString("criteria_type"));
            long value = metric == null ? 0 : ((Number) m.get(metric)).longValue();
            if (value >= rs.getInt("threshold")) {
                jdbc.update("insert into user_badges (user_id, badge_id) values (?, ?) on conflict do nothing",
                        userId, rs.getLong("id"));
                granted.add(new Badge(rs.getString("code"), rs.getString("name"), rs.getString("description"),
                        rs.getString("icon")));
            }
        }, userId);
        return granted;
    }
}
