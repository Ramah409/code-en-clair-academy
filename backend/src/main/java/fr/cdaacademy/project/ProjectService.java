package fr.cdaacademy.project;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.course.CourseAccess;
import fr.cdaacademy.progress.ProgressService;
import fr.cdaacademy.progress.ProgressService.Reward;

/**
 * Projets pratiques guidés. Une étape est validée quand toute sa liste de contrôle est cochée et,
 * si elle en comporte un, quand son exercice est réussi. La correction complète du projet n'est
 * dévoilée qu'une fois toutes les étapes validées.
 */
@Service
public class ProjectService {

    static final int STEP_XP = 20;
    static final int PROJECT_BONUS_XP = 80;

    public record StepExercise(String slug, String title, String kind, boolean solved) {
    }

    public record Step(int position, String title, String instructions, String deliverable, List<String> checklist,
            StepExercise exercise, boolean done) {
    }

    public record ProjectSummary(String slug, String title, String difficulty, String courseSlug, String courseTitle,
            int stepCount, int stepsDone, boolean available) {
    }

    public record ProjectView(String slug, String title, String difficulty, String courseSlug, String courseTitle,
            boolean available, String statement, String needs, String businessRules, String userStories,
            String mockup, String mcd, String mld, String acceptanceCriteria, String tests, List<Step> steps,
            boolean completed, String correction, String improvements) {
    }

    public record StepResult(Reward reward, boolean projectCompleted) {
    }

    private final JdbcTemplate jdbc;
    private final CourseAccess access;
    private final ProgressService progress;

    public ProjectService(JdbcTemplate jdbc, CourseAccess access, ProgressService progress) {
        this.jdbc = jdbc;
        this.access = access;
        this.progress = progress;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list(long userId, boolean admin) {
        return jdbc.query("""
                select p.slug, p.title, p.difficulty, c.slug as course_slug, c.title as course_title,
                       (select count(*) from project_steps s where s.project_id = p.id) as steps,
                       (select count(*) from project_steps s join user_project_steps u on u.step_id = s.id
                         where s.project_id = p.id and u.user_id = ?) as done
                from projects p left join courses c on c.id = p.course_id
                order by p.position
                """, (rs, i) -> new ProjectSummary(rs.getString("slug"), rs.getString("title"),
                rs.getString("difficulty"), rs.getString("course_slug"), rs.getString("course_title"),
                rs.getInt("steps"), rs.getInt("done"), available(userId, admin, rs.getString("course_slug"))),
                userId);
    }

    @Transactional(readOnly = true)
    public ProjectView view(long userId, boolean admin, String slug) {
        Map<String, Object> p = project(slug);
        String courseSlug = (String) p.get("course_slug");
        boolean available = available(userId, admin, courseSlug);
        List<Step> steps = steps(userId, ((Number) p.get("id")).longValue());
        boolean completed = !steps.isEmpty() && steps.stream().allMatch(Step::done);
        boolean showCorrection = completed || admin;
        return new ProjectView(slug, (String) p.get("title"), (String) p.get("difficulty"), courseSlug,
                (String) p.get("course_title"), available, (String) p.get("statement"), (String) p.get("needs"),
                (String) p.get("business_rules"), (String) p.get("user_stories"), (String) p.get("mockup"),
                (String) p.get("mcd"), (String) p.get("mld"), (String) p.get("acceptance_criteria"),
                (String) p.get("tests"), steps, completed, showCorrection ? (String) p.get("correction") : null,
                showCorrection ? (String) p.get("improvements") : null);
    }

    @Transactional
    public StepResult completeStep(long userId, boolean admin, String slug, int position, Set<Integer> checked) {
        Map<String, Object> p = project(slug);
        if (!available(userId, admin, (String) p.get("course_slug"))) {
            throw new BusinessRuleException("Le projet s'ouvre quand tous les QCM de chapitre du parcours sont réussis.");
        }
        long projectId = ((Number) p.get("id")).longValue();
        List<Step> steps = steps(userId, projectId);
        Step step = steps.stream().filter(s -> s.position() == position).findFirst()
                .orElseThrow(() -> new NotFoundException("Étape introuvable."));
        if (step.done()) {
            return new StepResult(Reward.none(), steps.stream().allMatch(Step::done));
        }
        if (steps.stream().anyMatch(s -> s.position() < position && !s.done())) {
            throw new BusinessRuleException("Valide d'abord les étapes précédentes.");
        }
        Set<Integer> expected = java.util.stream.IntStream.range(0, step.checklist().size()).boxed()
                .collect(Collectors.toSet());
        if (checked == null || !checked.containsAll(expected)) {
            throw new BusinessRuleException("Coche chaque point de la liste de contrôle avant de valider l'étape.");
        }
        if (step.exercise() != null && !step.exercise().solved()) {
            throw new BusinessRuleException("Réussis d'abord l'exercice de cette étape : « " + step.exercise().title() + " ».");
        }
        jdbc.update("""
                insert into user_project_steps (user_id, step_id)
                select ?, id from project_steps where project_id = ? and position = ?
                on conflict do nothing
                """, userId, projectId, position);
        boolean completed = steps.stream().allMatch(s -> s.done() || s.position() == position);
        Reward reward = progress.award(userId, STEP_XP + (completed ? PROJECT_BONUS_XP : 0), 0, false);
        return new StepResult(reward, completed);
    }

    private boolean available(long userId, boolean admin, String courseSlug) {
        return admin || courseSlug == null || access.load(userId, false, courseSlug).allChaptersPassed();
    }

    private Map<String, Object> project(String slug) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.*, c.slug as course_slug, c.title as course_title
                from projects p left join courses c on c.id = p.course_id where p.slug = ?
                """, slug);
        if (rows.isEmpty()) {
            throw new NotFoundException("Projet introuvable.");
        }
        return rows.getFirst();
    }

    private List<Step> steps(long userId, long projectId) {
        return jdbc.query("""
                select s.position, s.title, s.instructions, s.deliverable, s.checklist,
                       e.slug as ex_slug, e.title as ex_title, e.kind as ex_kind,
                       exists (select 1 from exercise_states es where es.exercise_id = e.id and es.user_id = ?
                               and es.solved_at is not null) as ex_solved,
                       exists (select 1 from user_project_steps u where u.step_id = s.id and u.user_id = ?) as done
                from project_steps s left join exercises e on e.id = s.exercise_id
                where s.project_id = ? order by s.position
                """, (rs, i) -> new Step(rs.getInt("position"), rs.getString("title"), rs.getString("instructions"),
                rs.getString("deliverable"),
                rs.getString("checklist").isBlank() ? List.of() : List.of(rs.getString("checklist").split("\n")),
                rs.getString("ex_slug") == null ? null : new StepExercise(rs.getString("ex_slug"),
                        rs.getString("ex_title"), rs.getString("ex_kind"), rs.getBoolean("ex_solved")),
                rs.getBoolean("done")), userId, userId, projectId);
    }
}
