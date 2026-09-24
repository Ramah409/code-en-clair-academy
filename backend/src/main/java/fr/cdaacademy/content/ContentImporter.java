package fr.cdaacademy.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.cdaacademy.config.AppProperties;
import fr.cdaacademy.content.ContentFiles.ChapterFile;
import fr.cdaacademy.content.ContentFiles.ChoiceDef;
import fr.cdaacademy.content.ContentFiles.CourseFile;
import fr.cdaacademy.content.ContentFiles.ExamFile;
import fr.cdaacademy.content.ContentFiles.ExerciseDef;
import fr.cdaacademy.content.ContentFiles.LessonDef;
import fr.cdaacademy.content.ContentFiles.ProjectDef;
import fr.cdaacademy.content.ContentFiles.ProjectStepDef;
import fr.cdaacademy.content.ContentFiles.QuestionDef;
import fr.cdaacademy.content.ContentLoader.CourseBundle;

/**
 * Charge les parcours décrits dans les fichiers YAML au démarrage.
 *
 * Un parcours n'est (ré)importé que si la version du fichier est supérieure à celle enregistrée :
 * les modifications faites dans l'espace d'administration sont conservées tant que le fichier
 * n'évolue pas. Lors d'une montée de version, le fichier fait foi : les éléments qui n'y figurent
 * plus sont retirés. Les identifiants existants sont conservés (mise à jour par slug), la
 * progression des apprenantes est donc préservée.
 */
@Component
@Order(10)
public class ContentImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentImporter.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Path contentRoot;

    public ContentImporter(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper json, AppProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
        this.contentRoot = Path.of(props.content().directory());
    }

    @Override
    public void run(ApplicationArguments args) {
        List<CourseBundle> bundles;
        try {
            bundles = new ContentLoader().loadAll(contentRoot);
        } catch (ContentLoader.ContentException e) {
            log.error("Contenu pédagogique non chargé : {}", e.getMessage());
            return;
        }
        if (bundles.isEmpty()) {
            log.warn("Aucun parcours trouvé dans {}", contentRoot.toAbsolutePath());
        }
        for (CourseBundle bundle : bundles) {
            Integer stored = jdbc.query("select content_version from courses where slug = ?",
                    rs -> rs.next() ? rs.getInt(1) : null, bundle.course().slug());
            if (stored != null && stored >= bundle.course().version()) {
                continue;
            }
            tx.executeWithoutResult(status -> importCourse(bundle));
            log.info("Parcours « {} » importé (version {})", bundle.course().title(), bundle.course().version());
        }
        importExams();
    }

    // ------------------------------------------------------------------ examens

    private void importExams() {
        List<ExamFile> exams;
        try {
            exams = new ContentLoader().loadExams(contentRoot);
        } catch (ContentLoader.ContentException e) {
            log.error("Examens non chargés : {}", e.getMessage());
            return;
        }
        for (ExamFile e : exams) {
            Integer stored = jdbc.query("select content_version from exams where slug = ?",
                    rs -> rs.next() ? rs.getInt(1) : null, e.slug());
            if (stored != null && stored >= e.version()) {
                continue;
            }
            ObjectNode content = e.content() != null && e.content().isObject() ? ((ObjectNode) e.content()).deepCopy()
                    : json.createObjectNode();
            if (e.courses() != null && !e.courses().isEmpty()) {
                content.set("courses", json.valueToTree(e.courses()));
            }
            jdbc.update("""
                    insert into exams (slug, kind, title, description, duration_minutes, passing_score, question_count,
                                       content, final_exam, position, level, content_version)
                    values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    on conflict (slug) do update set kind = excluded.kind, title = excluded.title,
                        description = excluded.description, duration_minutes = excluded.duration_minutes,
                        passing_score = excluded.passing_score, question_count = excluded.question_count,
                        content = excluded.content, final_exam = excluded.final_exam, position = excluded.position,
                        level = excluded.level, content_version = excluded.content_version, updated_at = now()
                    """, e.slug(), e.kind(), e.title(), e.description().strip(), e.durationMinutes(),
                    e.passingScore() == null ? 70 : e.passingScore(), e.questionCount(), toJson(content),
                    Boolean.TRUE.equals(e.finalExam()), e.position(), e.level(), e.version());
            log.info("Examen « {} » importé (version {})", e.title(), e.version());
        }
    }

    // ------------------------------------------------------------------ parcours

    void importCourse(CourseBundle bundle) {
        CourseFile c = bundle.course();
        // Position déjà prise par un autre parcours (réordonnancement du catalogue) : on lui attribue une place
        // libre ; il retrouvera sa position définitive à l'import de son propre fichier.
        jdbc.update("""
                update courses set level_number = (select min(n) from generate_series(1, 20) n
                                                   where n not in (select level_number from courses))
                where level_number = ? and slug <> ?
                """, c.position(), c.slug());
        long courseId = jdbc.queryForObject("""
                insert into courses (slug, level_number, title, summary, description, category, icon, content_version,
                                     exam_question_count, exam_time_limit_minutes, mandatory)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (slug) do update set level_number = excluded.level_number, title = excluded.title,
                    summary = excluded.summary, description = excluded.description, category = excluded.category,
                    icon = excluded.icon, content_version = excluded.content_version,
                    exam_question_count = excluded.exam_question_count,
                    exam_time_limit_minutes = excluded.exam_time_limit_minutes, mandatory = excluded.mandatory,
                    updated_at = now()
                returning id
                """, Long.class, c.slug(), c.position(), c.title(), c.summary(), c.description(), c.category(),
                c.icon() == null ? "book" : c.icon(), c.version(),
                c.examQuestionCount() == null ? 40 : c.examQuestionCount(), c.examTimeLimitMinutes(),
                c.isMandatory());

        List<String> chapterSlugs = new ArrayList<>();
        List<String> questionCodes = new ArrayList<>();
        List<String> exerciseSlugs = new ArrayList<>();
        jdbc.update("update modules set position = position + 1000 where course_id = ?", courseId);
        for (ChapterFile chapter : bundle.chapters()) {
            chapterSlugs.add(chapter.slug());
            importChapter(courseId, chapter, questionCodes, exerciseSlugs);
        }
        deleteNotIn("modules", "slug", "course_id", courseId, chapterSlugs);
        deleteNotIn("questions", "code", "course_id", courseId, questionCodes);
        deleteNotIn("exercises", "slug", "course_id", courseId, exerciseSlugs);

        if (c.project() != null) {
            importProject(courseId, c.position(), c.project());
        }
    }

    private void importChapter(long courseId, ChapterFile ch, List<String> questionCodes,
            List<String> exerciseSlugs) {
        long moduleId = jdbc.queryForObject("""
                insert into modules (course_id, slug, title, summary, position, level, quiz_question_count)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict on constraint ux_modules_course_slug do update set title = excluded.title,
                    summary = excluded.summary, position = excluded.position, level = excluded.level,
                    quiz_question_count = excluded.quiz_question_count
                returning id
                """, Long.class, courseId, ch.slug(), ch.title(), ch.summary(), ch.position(), ch.level(),
                ch.quizQuestionCount() == null ? 15 : ch.quizQuestionCount());

        List<String> lessonSlugs = new ArrayList<>();

        List<LessonDef> lessons = ch.lessons() == null ? List.of() : ch.lessons();
        // Libère les positions pour permettre le réordonnancement (contrainte différée en fin de transaction)
        jdbc.update("update lessons set position = position + 1000 where module_id = ?", moduleId);
        for (int i = 0; i < lessons.size(); i++) {
            LessonDef l = lessons.get(i);
            lessonSlugs.add(l.slug());
            long lessonId = jdbc.queryForObject("""
                    insert into lessons (module_id, slug, title, position, duration_minutes, prerequisites, objective,
                                         memo, common_mistakes, blocks, xp_reward)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    on conflict (slug) do update set module_id = excluded.module_id, title = excluded.title,
                        position = excluded.position, duration_minutes = excluded.duration_minutes,
                        prerequisites = excluded.prerequisites, objective = excluded.objective, memo = excluded.memo,
                        common_mistakes = excluded.common_mistakes, blocks = excluded.blocks,
                        xp_reward = excluded.xp_reward, updated_at = now()
                    returning id
                    """, Long.class, moduleId, l.slug(), l.title(), i + 1, l.duration(), l.prerequisites(),
                    l.objective() == null ? "" : l.objective(), l.memo(), l.commonMistakes(),
                    toJson(l.blocks() == null ? List.of() : l.blocks()), l.xp() == null ? 10 : l.xp());

            jdbc.update("delete from lesson_skills where lesson_id = ?", lessonId);
            for (String skill : l.skills() == null ? List.<String>of() : l.skills()) {
                jdbc.update("""
                        insert into lesson_skills (lesson_id, skill_id)
                        select ?, id from skills where code = ? on conflict do nothing
                        """, lessonId, skill);
            }
            List<QuestionDef> questions = l.questions() == null ? List.of() : l.questions();
            for (int q = 0; q < questions.size(); q++) {
                questionCodes.add(questions.get(q).code());
                upsertQuestion(courseId, moduleId, lessonId, q + 1, questions.get(q));
            }
            List<ExerciseDef> exercises = l.exercises() == null ? List.of() : l.exercises();
            for (int e = 0; e < exercises.size(); e++) {
                exerciseSlugs.add(exercises.get(e).slug());
                upsertExercise(courseId, lessonId, e + 1, exercises.get(e));
            }
        }
        List<ExerciseDef> chapterExercises = ch.exercises() == null ? List.of() : ch.exercises();
        for (int e = 0; e < chapterExercises.size(); e++) {
            exerciseSlugs.add(chapterExercises.get(e).slug());
            upsertExercise(courseId, null, e + 1, chapterExercises.get(e));
        }
        List<QuestionDef> bank = ch.bank() == null ? List.of() : ch.bank();
        for (int q = 0; q < bank.size(); q++) {
            questionCodes.add(bank.get(q).code());
            upsertQuestion(courseId, moduleId, null, q + 1, bank.get(q));
        }

        deleteNotIn("lessons", "slug", "module_id", moduleId, lessonSlugs);
    }

    // ------------------------------------------------------------------ questions et exercices

    private void upsertQuestion(long courseId, long moduleId, Long lessonId, int position, QuestionDef q) {
        String expected = q.answers() == null ? null : String.join("\n", q.answers());
        long questionId = jdbc.queryForObject("""
                insert into questions (code, course_id, module_id, lesson_id, skill_id, position, kind, prompt,
                                       expected_answer, explanation, difficulty, code_snippet, code_language)
                values (?, ?, ?, ?, (select id from skills where code = ?), ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (code) do update set course_id = excluded.course_id, module_id = excluded.module_id,
                    lesson_id = excluded.lesson_id, skill_id = excluded.skill_id, position = excluded.position,
                    kind = excluded.kind, prompt = excluded.prompt, expected_answer = excluded.expected_answer,
                    explanation = excluded.explanation, difficulty = excluded.difficulty,
                    code_snippet = excluded.code_snippet, code_language = excluded.code_language, updated_at = now()
                returning id
                """, Long.class, q.code(), courseId, moduleId, lessonId, q.skill(), position, q.kind(),
                q.prompt().strip(), expected, q.explanation().strip(), q.difficulty() == null ? 1 : q.difficulty(),
                q.snippet() == null ? null : q.snippet().stripTrailing(), q.language());

        List<ChoiceDef> choices = q.choices() == null ? List.of() : q.choices();
        for (int i = 0; i < choices.size(); i++) {
            ChoiceDef c = choices.get(i);
            jdbc.update("""
                    insert into choices (question_id, position, label, correct, explanation) values (?, ?, ?, ?, ?)
                    on conflict on constraint ux_choices_question_position do update
                    set label = excluded.label, correct = excluded.correct, explanation = excluded.explanation
                    """, questionId, i + 1, c.label(), c.correct(), c.why().strip());
        }
        jdbc.update("delete from choices where question_id = ? and position > ?", questionId, choices.size());
    }

    private void upsertExercise(long courseId, Long lessonId, int position, ExerciseDef e) {
        List<String> hints = e.hints();
        jdbc.update("""
                insert into exercises (slug, course_id, lesson_id, skill_id, kind, purpose, difficulty, position, title,
                                       statement, success_criteria, hint_1, hint_2, hint_3, solution, explanation,
                                       xp_reward, payload)
                values (?, ?, ?, (select id from skills where code = ?), ?, 'EXERCICE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?::jsonb)
                on conflict (slug) do update set course_id = excluded.course_id, lesson_id = excluded.lesson_id,
                    skill_id = excluded.skill_id, kind = excluded.kind, difficulty = excluded.difficulty,
                    position = excluded.position, title = excluded.title, statement = excluded.statement,
                    success_criteria = excluded.success_criteria, hint_1 = excluded.hint_1, hint_2 = excluded.hint_2,
                    hint_3 = excluded.hint_3, solution = excluded.solution, explanation = excluded.explanation,
                    xp_reward = excluded.xp_reward, payload = excluded.payload, updated_at = now()
                """, e.slug(), courseId, lessonId, e.skill(), e.kind(),
                e.difficulty() == null ? "FACILE" : e.difficulty(), position, e.title(), e.statement().strip(),
                e.criteria() == null ? "" : e.criteria().strip(), hints.get(0).strip(), hints.get(1).strip(),
                hints.get(2).strip(), e.solution().stripTrailing(), e.explanation().strip(),
                e.xp() == null ? 15 : e.xp(), toJson(e.payload()));
    }

    // ------------------------------------------------------------------ projet de fin de parcours

    private void importProject(long courseId, int coursePosition, ProjectDef p) {
        long projectId = jdbc.queryForObject("""
                insert into projects (slug, position, title, difficulty, statement, needs, business_rules, user_stories,
                                      mockup, mcd, mld, acceptance_criteria, tests, correction, improvements, course_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (slug) do update set title = excluded.title, difficulty = excluded.difficulty,
                    statement = excluded.statement, needs = excluded.needs, business_rules = excluded.business_rules,
                    user_stories = excluded.user_stories, mockup = excluded.mockup, mcd = excluded.mcd,
                    mld = excluded.mld, acceptance_criteria = excluded.acceptance_criteria, tests = excluded.tests,
                    correction = excluded.correction, improvements = excluded.improvements,
                    course_id = excluded.course_id, updated_at = now()
                returning id
                """, Long.class, p.slug(), coursePosition, p.title(), p.difficulty(), p.statement(), p.needs(),
                p.businessRules(), p.userStories(), nz(p.mockup()), nz(p.mcd()), nz(p.mld()), p.acceptanceCriteria(),
                nz(p.tests()), p.correction(), nz(p.improvements()), courseId);

        List<ProjectStepDef> steps = p.steps() == null ? List.of() : p.steps();
        for (int i = 0; i < steps.size(); i++) {
            ProjectStepDef s = steps.get(i);
            jdbc.update("""
                    insert into project_steps (project_id, position, title, instructions, deliverable, checklist,
                                               exercise_id)
                    values (?, ?, ?, ?, ?, ?, (select id from exercises where slug = ?))
                    on conflict on constraint ux_project_steps_position do update set title = excluded.title,
                        instructions = excluded.instructions, deliverable = excluded.deliverable,
                        checklist = excluded.checklist, exercise_id = excluded.exercise_id
                    """, projectId, i + 1, s.title(), s.instructions(), s.deliverable(),
                    String.join("\n", s.checklist() == null ? List.of() : s.checklist()), s.exercise());
        }
        jdbc.update("delete from project_steps where project_id = ? and position > ?", projectId, steps.size());
    }

    // ------------------------------------------------------------------ utilitaires

    /** Supprime les éléments d'un périmètre absents du fichier (les éléments sans clé sont conservés). */
    private void deleteNotIn(String table, String keyColumn, String scopeColumn, long scopeId, List<String> keep) {
        jdbc.update("delete from " + table + " where " + scopeColumn + " = ? and not (" + keyColumn + " = any (?))",
                ps -> {
                    ps.setLong(1, scopeId);
                    ps.setArray(2, ps.getConnection().createArrayOf("varchar", keep.toArray()));
                });
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
