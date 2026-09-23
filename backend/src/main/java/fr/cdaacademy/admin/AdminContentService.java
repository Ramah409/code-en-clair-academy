package fr.cdaacademy.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.common.ConflictException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.common.PageResponse;
import fr.cdaacademy.exercise.ExerciseService;

/**
 * Administration des contenus : parcours, chapitres, leçons, questions et exercices.
 *
 * Les suppressions en cascade effaceraient aussi la progression des apprenantes : un élément déjà
 * utilisé ne peut donc pas être supprimé, il doit être dépublié. Les contenus issus des fichiers
 * YAML restent modifiables ici ; ils seront remplacés si le fichier du parcours change de version.
 */
@Service
public class AdminContentService {

    static final Set<String> LEVELS = Set.of("DEBUTANT", "INTERMEDIAIRE", "AVANCE", "EXAMEN");
    static final Set<String> QUESTION_KINDS = Set.of("CHOIX_UNIQUE", "CHOIX_MULTIPLE", "VRAI_FAUX", "TEXTE",
            "COMPLETER_CODE", "RESULTAT_CODE");
    static final Set<String> SINGLE = Set.of("CHOIX_UNIQUE", "VRAI_FAUX", "RESULTAT_CODE");
    static final Set<String> EXERCISE_KINDS = Set.of("SQL", "CODE_LIBRE", "CORRIGER_ERREUR", "COMPLETER",
            "REMETTRE_ORDRE", "MCD", "MCD_VERS_MLD", "MLD_VERS_SQL");
    static final Set<String> DIFFICULTIES = Set.of("FACILE", "INTERMEDIAIRE", "DIFFICILE");
    static final Set<String> BLOCK_TYPES = Set.of("text", "definition", "code", "demo", "callout", "question",
            "exercise", "steps", "compare", "quiz", "jury");
    static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";

    // ------------------------------------------------------------------ vues et requêtes

    public record CourseRow(long id, String slug, String title, String summary, String description, String category,
            String icon, boolean published, boolean mandatory, int position, int contentVersion, int examQuestionCount,
            Integer examTimeLimitMinutes, int chapters, int lessons, int questions, int exercises, int learners) {
    }

    public record CourseForm(String slug, String title, String summary, String description, String category,
            String icon, Boolean published, Boolean mandatory, Integer examQuestionCount,
            Integer examTimeLimitMinutes) {
    }

    public record LessonItem(long id, String slug, String title, int position, int duration, boolean published) {
    }

    public record ChapterRow(long id, String slug, String title, String summary, String level, int position,
            int quizQuestionCount, int bankSize, List<LessonItem> lessons) {
    }

    public record ChapterForm(String slug, String title, String summary, String level, Integer quizQuestionCount) {
    }

    public record LessonDetail(long id, long chapterId, long courseId, String courseSlug, String slug, String title,
            String objective, String prerequisites, int duration, int xp, String memo, String commonMistakes,
            boolean published, JsonNode blocks, List<Map<String, Object>> questions, List<Map<String, Object>> exercises) {
    }

    public record LessonForm(String slug, String title, String objective, String prerequisites, Integer duration,
            Integer xp, String memo, String commonMistakes, Boolean published, JsonNode blocks) {
    }

    public record QuestionRow(long id, String code, String kind, String prompt, int difficulty, String courseTitle,
            String chapterTitle, String lessonTitle, boolean published) {
    }

    public record ChoiceForm(String label, boolean correct, String why) {
    }

    public record QuestionForm(String code, String kind, Integer difficulty, String prompt, String snippet,
            String language, String explanation, List<String> answers, List<ChoiceForm> choices, Long courseId,
            Long chapterId, Long lessonId, Boolean published) {
    }

    public record QuestionDetail(long id, String code, String kind, int difficulty, String prompt, String snippet,
            String language, String explanation, List<String> answers, List<ChoiceForm> choices, Long courseId,
            Long chapterId, Long lessonId, boolean published, int usage) {
    }

    public record ExerciseRow(long id, String slug, String kind, String title, String difficulty, String courseTitle,
            String lessonTitle, int attempts) {
    }

    public record ExerciseForm(String slug, String kind, String title, String difficulty, String statement,
            String criteria, List<String> hints, String solution, String explanation, Integer xp, JsonNode payload,
            Long courseId, Long lessonId) {
    }

    public record ExerciseDetail(long id, String slug, String kind, String title, String difficulty, String statement,
            String criteria, List<String> hints, String solution, String explanation, int xp, JsonNode payload,
            Long courseId, Long lessonId, int attempts) {
    }

    public record CheckResult(boolean success, String message, List<String> problems) {
    }

    public record ExerciseSaved(ExerciseDetail exercise, CheckResult check) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ExerciseService exercises;

    public AdminContentService(JdbcTemplate jdbc, ObjectMapper json, ExerciseService exercises) {
        this.jdbc = jdbc;
        this.json = json;
        this.exercises = exercises;
    }

    // ------------------------------------------------------------------ parcours

    public List<CourseRow> courses() {
        return jdbc.query("""
                select c.id, c.slug, c.title, c.summary, c.description, c.category, c.icon, c.published, c.mandatory,
                       c.level_number, c.content_version, c.exam_question_count, c.exam_time_limit_minutes,
                       (select count(*) from modules m where m.course_id = c.id),
                       (select count(*) from lessons l join modules m on m.id = l.module_id where m.course_id = c.id),
                       (select count(*) from questions q where q.course_id = c.id),
                       (select count(*) from exercises e where e.course_id = c.id),
                       (select count(distinct p.user_id) from progress p join lessons l on l.id = p.lesson_id
                          join modules m on m.id = l.module_id where m.course_id = c.id)
                from courses c order by c.level_number
                """, (rs, i) -> new CourseRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8), rs.getBoolean(9), rs.getInt(10),
                rs.getInt(11), rs.getInt(12), (Integer) rs.getObject(13), rs.getInt(14), rs.getInt(15), rs.getInt(16),
                rs.getInt(17), rs.getInt(18)));
    }

    private CourseRow course(long id) {
        return courses().stream().filter(c -> c.id() == id).findFirst()
                .orElseThrow(() -> new NotFoundException("Parcours introuvable."));
    }

    @Transactional
    public CourseRow createCourse(CourseForm f) {
        String slug = slug(f.slug(), "slug du parcours");
        requireText(f.title(), "titre", 150);
        requireText(f.summary(), "résumé", 500);
        unique("courses", "slug", slug, null);
        long id = jdbc.queryForObject("""
                insert into courses (slug, level_number, title, summary, description, category, icon, published, mandatory,
                                     exam_question_count, exam_time_limit_minutes)
                values (?, (select coalesce(max(level_number), 0) + 1 from courses), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class, slug, f.title().strip(), f.summary().strip(),
                f.description() == null ? f.summary().strip() : f.description().strip(),
                f.category() == null || f.category().isBlank() ? "PROJET" : f.category().strip(),
                f.icon() == null || f.icon().isBlank() ? "book" : f.icon().strip(),
                !Boolean.FALSE.equals(f.published()), !Boolean.FALSE.equals(f.mandatory()),
                examCount(f.examQuestionCount()), f.examTimeLimitMinutes());
        return course(id);
    }

    @Transactional
    public CourseRow updateCourse(long id, CourseForm f) {
        course(id);
        requireText(f.title(), "titre", 150);
        requireText(f.summary(), "résumé", 500);
        requireText(f.description(), "description", 20_000);
        jdbc.update("""
                update courses set title = ?, summary = ?, description = ?, category = ?, icon = ?, published = ?,
                    mandatory = ?, exam_question_count = ?, exam_time_limit_minutes = ?, updated_at = now()
                where id = ?
                """, f.title().strip(), f.summary().strip(), f.description().strip(),
                f.category() == null || f.category().isBlank() ? "PROJET" : f.category().strip(),
                f.icon() == null || f.icon().isBlank() ? "book" : f.icon().strip(),
                !Boolean.FALSE.equals(f.published()), !Boolean.FALSE.equals(f.mandatory()),
                examCount(f.examQuestionCount()), f.examTimeLimitMinutes(), id);
        return course(id);
    }

    @Transactional
    public void deleteCourse(long id) {
        CourseRow c = course(id);
        Integer attempts = jdbc.queryForObject("select count(*) from quiz_attempts where course_id = ?", Integer.class, id);
        if (c.learners() > 0 || (attempts != null && attempts > 0)) {
            throw new ConflictException("Des apprenantes ont déjà commencé ce parcours : dépublie-le plutôt que de le "
                    + "supprimer, pour conserver leur progression.");
        }
        jdbc.update("delete from courses where id = ?", id);
    }

    // ------------------------------------------------------------------ chapitres

    public List<ChapterRow> chapters(long courseId) {
        course(courseId);
        List<ChapterRow> rows = jdbc.query("""
                select m.id, m.slug, m.title, m.summary, m.level, m.position, m.quiz_question_count,
                       (select count(*) from questions q where q.module_id = m.id and q.published)
                from modules m where m.course_id = ? order by m.position
                """, (rs, i) -> new ChapterRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), new ArrayList<>()), courseId);
        for (ChapterRow ch : rows) {
            ch.lessons().addAll(jdbc.query("""
                    select id, slug, title, position, duration_minutes, published from lessons where module_id = ?
                    order by position
                    """, (rs, i) -> new LessonItem(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                    rs.getInt(5), rs.getBoolean(6)), ch.id()));
        }
        return rows;
    }

    @Transactional
    public List<ChapterRow> createChapter(long courseId, ChapterForm f) {
        course(courseId);
        String slug = slug(f.slug(), "slug du chapitre");
        unique("modules", "slug", slug, null);
        validateChapter(f);
        jdbc.update("""
                insert into modules (course_id, slug, title, summary, position, level, quiz_question_count)
                values (?, ?, ?, ?, (select coalesce(max(position), 0) + 1 from modules where course_id = ?), ?, ?)
                """, courseId, slug, f.title().strip(), f.summary().strip(), courseId, f.level(), quizCount(f));
        return chapters(courseId);
    }

    @Transactional
    public List<ChapterRow> updateChapter(long id, ChapterForm f) {
        long courseId = courseOfChapter(id);
        validateChapter(f);
        jdbc.update("update modules set title = ?, summary = ?, level = ?, quiz_question_count = ? where id = ?",
                f.title().strip(), f.summary().strip(), f.level(), quizCount(f), id);
        return chapters(courseId);
    }

    @Transactional
    public List<ChapterRow> deleteChapter(long id) {
        long courseId = courseOfChapter(id);
        Integer used = jdbc.queryForObject("""
                select (select count(*) from progress p join lessons l on l.id = p.lesson_id where l.module_id = ?)
                     + (select count(*) from quiz_attempts a where a.module_id = ?)
                """, Integer.class, id, id);
        if (used != null && used > 0) {
            throw new ConflictException("Ce chapitre a déjà été suivi par des apprenantes : dépublie ses leçons plutôt "
                    + "que de le supprimer.");
        }
        jdbc.update("delete from modules where id = ?", id);
        return chapters(courseId);
    }

    @Transactional
    public List<ChapterRow> moveChapter(long id, int delta) {
        long courseId = courseOfChapter(id);
        swap("modules", "course_id", courseId, id, delta);
        return chapters(courseId);
    }

    // ------------------------------------------------------------------ leçons

    public LessonDetail lesson(long id) {
        List<LessonDetail> found = jdbc.query("""
                select l.id, l.module_id, m.course_id, c.slug, l.slug, l.title, l.objective, l.prerequisites,
                       l.duration_minutes, l.xp_reward, l.memo, l.common_mistakes, l.published, l.blocks::text
                from lessons l join modules m on m.id = l.module_id join courses c on c.id = m.course_id where l.id = ?
                """, (rs, i) -> new LessonDetail(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getInt(9), rs.getInt(10),
                rs.getString(11), rs.getString(12), rs.getBoolean(13), read(rs.getString(14)),
                jdbc.queryForList("select code, kind, left(prompt, 120) as prompt from questions where lesson_id = ? order by position",
                        rs.getLong(1)),
                jdbc.queryForList("select slug, kind, title from exercises where lesson_id = ? order by position",
                        rs.getLong(1))), id);
        if (found.isEmpty()) {
            throw new NotFoundException("Leçon introuvable.");
        }
        return found.getFirst();
    }

    @Transactional
    public LessonDetail createLesson(long chapterId, LessonForm f) {
        courseOfChapter(chapterId);
        String slug = slug(f.slug(), "slug de la leçon");
        unique("lessons", "slug", slug, null);
        requireText(f.title(), "titre", 150);
        requireText(f.objective(), "objectif", 1000);
        long id = jdbc.queryForObject("""
                insert into lessons (module_id, slug, title, position, duration_minutes, objective, xp_reward, blocks,
                                     published)
                values (?, ?, ?, (select coalesce(max(position), 0) + 1 from lessons where module_id = ?), ?, ?, ?,
                        '[]'::jsonb, false)
                returning id
                """, Long.class, chapterId, slug, f.title().strip(), chapterId, duration(f.duration()),
                f.objective().strip(), xp(f.xp()));
        return lesson(id);
    }

    @Transactional
    public LessonDetail updateLesson(long id, LessonForm f) {
        lesson(id);
        requireText(f.title(), "titre", 150);
        requireText(f.objective(), "objectif", 1000);
        JsonNode blocks = f.blocks() == null ? json.createArrayNode() : f.blocks();
        validateBlocks(id, blocks);
        jdbc.update("""
                update lessons set title = ?, objective = ?, prerequisites = ?, duration_minutes = ?, xp_reward = ?,
                    memo = ?, common_mistakes = ?, published = ?, blocks = ?::jsonb, updated_at = now()
                where id = ?
                """, f.title().strip(), f.objective().strip(), blank(f.prerequisites()), duration(f.duration()),
                xp(f.xp()), blank(f.memo()), blank(f.commonMistakes()), !Boolean.FALSE.equals(f.published()),
                write(blocks), id);
        return lesson(id);
    }

    @Transactional
    public void deleteLesson(long id) {
        lesson(id);
        Integer used = jdbc.queryForObject("select count(*) from progress where lesson_id = ?", Integer.class, id);
        if (used != null && used > 0) {
            throw new ConflictException("Des apprenantes ont déjà ouvert cette leçon : dépublie-la plutôt que de la "
                    + "supprimer.");
        }
        jdbc.update("delete from lessons where id = ?", id);
    }

    @Transactional
    public LessonDetail moveLesson(long id, int delta) {
        LessonDetail l = lesson(id);
        swap("lessons", "module_id", l.chapterId(), id, delta);
        return lesson(id);
    }

    /** Vérifie la structure des blocs et que les références (questions, exercices, passages) existent. */
    void validateBlocks(long lessonId, JsonNode blocks) {
        if (!blocks.isArray()) {
            throw new BusinessRuleException("Les blocs doivent former une liste JSON ([ ... ]).");
        }
        Set<String> questions = new HashSet<>(jdbc.queryForList("select code from questions where lesson_id = ?",
                String.class, lessonId));
        Set<String> exerciseSlugs = new HashSet<>(jdbc.queryForList("select slug from exercises where lesson_id = ?",
                String.class, lessonId));
        Set<String> anchors = new HashSet<>();
        blocks.forEach(b -> {
            if (b.hasNonNull("id")) {
                anchors.add(b.path("id").asText());
            }
        });
        int i = 0;
        for (JsonNode b : blocks) {
            i++;
            String type = b.path("type").asText();
            String where = "bloc n° " + i + " (" + (type.isEmpty() ? "sans type" : type) + ")";
            if (!BLOCK_TYPES.contains(type)) {
                throw new BusinessRuleException(where + " : type inconnu. Types possibles : " + String.join(", ",
                        BLOCK_TYPES.stream().sorted().toList()) + ".");
            }
            switch (type) {
                case "question" -> {
                    if (!questions.contains(b.path("ref").asText())) {
                        throw new BusinessRuleException(where + " : la question « " + b.path("ref").asText()
                                + " » n'est pas rattachée à cette leçon.");
                    }
                    requireAnchor(b, anchors, where);
                }
                case "exercise" -> {
                    if (!exerciseSlugs.contains(b.path("ref").asText())) {
                        throw new BusinessRuleException(where + " : l'exercice « " + b.path("ref").asText()
                                + " » n'est pas rattaché à cette leçon.");
                    }
                }
                case "quiz" -> {
                    if (!b.path("items").isArray() || b.path("items").size() < 2) {
                        throw new BusinessRuleException(where + " : un QCM de fin de leçon demande au moins deux questions.");
                    }
                    for (JsonNode item : b.path("items")) {
                        if (!questions.contains(item.path("ref").asText())) {
                            throw new BusinessRuleException(where + " : la question « " + item.path("ref").asText()
                                    + " » n'est pas rattachée à cette leçon.");
                        }
                        requireAnchor(item, anchors, where);
                    }
                }
                case "jury", "steps" -> {
                    if (!b.path("items").isArray() || b.path("items").isEmpty()) {
                        throw new BusinessRuleException(where + " : la liste items est vide.");
                    }
                }
                case "text", "callout", "definition" -> {
                    if (!b.hasNonNull("md")) {
                        throw new BusinessRuleException(where + " : le texte (md) est obligatoire.");
                    }
                }
                case "code", "demo" -> {
                    if (!b.hasNonNull("code")) {
                        throw new BusinessRuleException(where + " : le code est obligatoire.");
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void requireAnchor(JsonNode node, Set<String> anchors, String where) {
        if (node.hasNonNull("review") && !anchors.contains(node.path("review").asText())) {
            throw new BusinessRuleException(where + " : « revoir le passage » vise un bloc inexistant ("
                    + node.path("review").asText() + ").");
        }
    }

    // ------------------------------------------------------------------ questions

    public PageResponse<QuestionRow> questions(Long courseId, Long chapterId, Long lessonId, String search, int page) {
        int size = 25;
        String like = search == null || search.isBlank() ? null : "%" + search.strip().toLowerCase() + "%";
        String where = """
                where (?::bigint is null or q.course_id = ?) and (?::bigint is null or q.module_id = ?)
                  and (?::bigint is null or q.lesson_id = ?)
                  and (?::text is null or lower(q.prompt) like ? or lower(q.code) like ?)
                """;
        Object[] args = { courseId, courseId, chapterId, chapterId, lessonId, lessonId, like, like, like };
        long total = jdbc.queryForObject("select count(*) from questions q " + where, Long.class, args);
        List<Object> all = new ArrayList<>(List.of(args));
        all.add(size);
        all.add(Math.max(page, 0) * size);
        List<QuestionRow> rows = jdbc.query("""
                select q.id, q.code, q.kind, left(q.prompt, 160), q.difficulty, c.title, m.title, l.title, q.published
                from questions q left join courses c on c.id = q.course_id left join modules m on m.id = q.module_id
                left join lessons l on l.id = q.lesson_id
                """ + where + " order by c.level_number nulls last, m.position nulls last, l.position nulls last, q.position, q.id limit ? offset ?",
                (rs, i) -> new QuestionRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getBoolean(9)),
                all.toArray());
        return new PageResponse<>(rows, Math.max(page, 0), size, total, (int) Math.ceil(total / (double) size));
    }

    public QuestionDetail question(long id) {
        List<QuestionDetail> found = jdbc.query("""
                select q.id, q.code, q.kind, q.difficulty, q.prompt, q.code_snippet, q.code_language, q.explanation,
                       q.expected_answer, q.course_id, q.module_id, q.lesson_id, q.published,
                       (select count(*) from quiz_attempt_items i where i.question_id = q.id)
                     + (select count(*) from user_question_stats s where s.question_id = q.id)
                from questions q where q.id = ?
                """, (rs, i) -> new QuestionDetail(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9) == null ? List.of() : List.of(rs.getString(9).split("\n")),
                jdbc.query("select label, correct, explanation from choices where question_id = ? order by position",
                        (r, j) -> new ChoiceForm(r.getString(1), r.getBoolean(2), r.getString(3)), rs.getLong(1)),
                (Long) rs.getObject(10), (Long) rs.getObject(11), (Long) rs.getObject(12), rs.getBoolean(13),
                rs.getInt(14)), id);
        if (found.isEmpty()) {
            throw new NotFoundException("Question introuvable.");
        }
        return found.getFirst();
    }

    @Transactional
    public QuestionDetail saveQuestion(Long id, QuestionForm f) {
        validateQuestion(f);
        Long courseId = f.courseId();
        Long chapterId = f.chapterId();
        if (f.lessonId() != null) {
            Map<String, Object> l = jdbc.queryForMap("""
                    select m.id as chapter, m.course_id as course from lessons l join modules m on m.id = l.module_id
                    where l.id = ?
                    """, f.lessonId());
            chapterId = ((Number) l.get("chapter")).longValue();
            courseId = ((Number) l.get("course")).longValue();
        } else if (chapterId != null) {
            courseId = jdbc.queryForObject("select course_id from modules where id = ?", Long.class, chapterId);
        }
        String expected = f.answers() == null || f.answers().isEmpty() ? null
                : String.join("\n", f.answers().stream().map(String::strip).filter(s -> !s.isEmpty()).toList());
        String code = f.code() == null || f.code().isBlank() ? "adm-" + UUID.randomUUID().toString().substring(0, 8)
                : slug(f.code(), "code de la question");
        unique("questions", "code", code, id);
        if (id == null) {
            id = jdbc.queryForObject("""
                    insert into questions (code, kind, difficulty, prompt, code_snippet, code_language, explanation,
                                           expected_answer, course_id, module_id, lesson_id, published, position)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            (select coalesce(max(position), 0) + 1 from questions where module_id is not distinct from ?))
                    returning id
                    """, Long.class, code, f.kind(), difficulty(f.difficulty()), f.prompt().strip(), blank(f.snippet()),
                    blank(f.language()), f.explanation().strip(), expected, courseId, chapterId, f.lessonId(),
                    !Boolean.FALSE.equals(f.published()), chapterId);
        } else {
            question(id);
            jdbc.update("""
                    update questions set code = ?, kind = ?, difficulty = ?, prompt = ?, code_snippet = ?, code_language = ?,
                        explanation = ?, expected_answer = ?, course_id = ?, module_id = ?, lesson_id = ?, published = ?,
                        updated_at = now()
                    where id = ?
                    """, code, f.kind(), difficulty(f.difficulty()), f.prompt().strip(), blank(f.snippet()),
                    blank(f.language()), f.explanation().strip(), expected, courseId, chapterId, f.lessonId(),
                    !Boolean.FALSE.equals(f.published()), id);
            jdbc.update("delete from choices where question_id = ?", id);
        }
        List<ChoiceForm> choices = f.choices() == null ? List.of() : f.choices();
        if (!Set.of("TEXTE", "COMPLETER_CODE").contains(f.kind())) {
            for (int i = 0; i < choices.size(); i++) {
                ChoiceForm c = choices.get(i);
                jdbc.update("insert into choices (question_id, position, label, correct, explanation) values (?, ?, ?, ?, ?)",
                        id, i + 1, c.label().strip(), c.correct(), c.why().strip());
            }
        }
        return question(id);
    }

    @Transactional
    public void deleteQuestion(long id) {
        QuestionDetail q = question(id);
        if (q.usage() > 0) {
            throw new ConflictException("Cette question a déjà été utilisée dans des QCM : dépublie-la plutôt que de "
                    + "la supprimer, pour garder l'historique des apprenantes.");
        }
        Integer referenced = jdbc.queryForObject("""
                select count(*) from lessons l, jsonb_array_elements(l.blocks) b
                where b ->> 'ref' = ? or exists (select 1 from jsonb_array_elements(coalesce(b -> 'items', '[]'::jsonb)) it
                                                  where it ->> 'ref' = ?)
                """, Integer.class, q.code(), q.code());
        if (referenced != null && referenced > 0) {
            throw new ConflictException("Cette question est affichée dans une leçon : retire-la d'abord des blocs de la leçon.");
        }
        jdbc.update("delete from questions where id = ?", id);
    }

    private void validateQuestion(QuestionForm f) {
        if (f.kind() == null || !QUESTION_KINDS.contains(f.kind())) {
            throw new BusinessRuleException("Type de question inconnu.");
        }
        requireText(f.prompt(), "énoncé", 5000);
        requireText(f.explanation(), "explication", 5000);
        if (Set.of("TEXTE", "COMPLETER_CODE").contains(f.kind())) {
            if (f.answers() == null || f.answers().stream().allMatch(a -> a == null || a.isBlank())) {
                throw new BusinessRuleException("Indique au moins une réponse acceptée.");
            }
            return;
        }
        List<ChoiceForm> choices = f.choices() == null ? List.of() : f.choices();
        if (choices.size() < 2 || choices.size() > 8) {
            throw new BusinessRuleException("Une question à choix demande entre 2 et 8 choix.");
        }
        long correct = choices.stream().filter(ChoiceForm::correct).count();
        if (correct == 0) {
            throw new BusinessRuleException("Coche au moins une bonne réponse.");
        }
        if (SINGLE.contains(f.kind()) && correct != 1) {
            throw new BusinessRuleException("Ce type de question demande exactement une bonne réponse.");
        }
        for (ChoiceForm c : choices) {
            if (c.label() == null || c.label().isBlank() || c.why() == null || c.why().isBlank()) {
                throw new BusinessRuleException("Chaque choix a un libellé et une explication (« pourquoi »).");
            }
        }
    }

    // ------------------------------------------------------------------ exercices

    public List<ExerciseRow> exerciseList(Long courseId, String search) {
        String like = search == null || search.isBlank() ? null : "%" + search.strip().toLowerCase() + "%";
        return jdbc.query("""
                select e.id, e.slug, e.kind, e.title, e.difficulty, c.title, l.title,
                       (select count(*) from attempts a where a.exercise_id = e.id)
                from exercises e left join courses c on c.id = e.course_id left join lessons l on l.id = e.lesson_id
                where (?::bigint is null or e.course_id = ?)
                  and (?::text is null or lower(e.title) like ? or lower(e.slug) like ?)
                order by c.level_number nulls last, e.position, e.id limit 300
                """, (rs, i) -> new ExerciseRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8)), courseId, courseId, like, like, like);
    }

    public ExerciseDetail exercise(long id) {
        List<ExerciseDetail> found = jdbc.query("""
                select id, slug, kind, title, difficulty, statement, success_criteria, hint_1, hint_2, hint_3, solution,
                       explanation, xp_reward, payload::text, course_id, lesson_id,
                       (select count(*) from attempts a where a.exercise_id = exercises.id)
                from exercises where id = ?
                """, (rs, i) -> new ExerciseDetail(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7),
                List.of(rs.getString(8), rs.getString(9), rs.getString(10)), rs.getString(11), rs.getString(12),
                rs.getInt(13), read(rs.getString(14)), (Long) rs.getObject(15), (Long) rs.getObject(16),
                rs.getInt(17)), id);
        if (found.isEmpty()) {
            throw new NotFoundException("Exercice introuvable.");
        }
        return found.getFirst();
    }

    @Transactional
    public ExerciseSaved saveExercise(Long id, ExerciseForm f, long adminId) {
        if (f.kind() == null || !EXERCISE_KINDS.contains(f.kind())) {
            throw new BusinessRuleException("Type d'exercice inconnu.");
        }
        String slug = slug(f.slug(), "slug de l'exercice");
        unique("exercises", "slug", slug, id);
        requireText(f.title(), "titre", 150);
        requireText(f.statement(), "énoncé", 20_000);
        requireText(f.solution(), "solution", 20_000);
        requireText(f.explanation(), "explication", 20_000);
        if (f.hints() == null || f.hints().size() != 3 || f.hints().stream().anyMatch(h -> h == null || h.isBlank())) {
            throw new BusinessRuleException("Écris exactement trois indices, du plus léger au plus précis.");
        }
        if (f.payload() == null || !f.payload().isObject()) {
            throw new BusinessRuleException("Le payload doit être un objet JSON ({ ... }).");
        }
        String difficulty = f.difficulty() == null || !DIFFICULTIES.contains(f.difficulty()) ? "FACILE" : f.difficulty();
        Long courseId = f.courseId();
        if (f.lessonId() != null) {
            courseId = jdbc.queryForObject(
                    "select m.course_id from lessons l join modules m on m.id = l.module_id where l.id = ?", Long.class,
                    f.lessonId());
        }
        Object[] values = { slug, f.kind(), difficulty, f.title().strip(), f.statement().strip(),
                f.criteria() == null || f.criteria().isBlank() ? "Correction automatique." : f.criteria().strip(),
                f.hints().get(0).strip(), f.hints().get(1).strip(), f.hints().get(2).strip(), f.solution(),
                f.explanation().strip(), xp(f.xp()), write(f.payload()), courseId, f.lessonId() };
        if (id == null) {
            id = jdbc.queryForObject("""
                    insert into exercises (slug, kind, difficulty, title, statement, success_criteria, hint_1, hint_2,
                                           hint_3, solution, explanation, xp_reward, payload, course_id, lesson_id, purpose)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'EXERCICE') returning id
                    """, Long.class, values);
        } else {
            exercise(id);
            List<Object> args = new ArrayList<>(List.of(values));
            args.add(id);
            jdbc.update("""
                    update exercises set slug = ?, kind = ?, difficulty = ?, title = ?, statement = ?, success_criteria = ?,
                        hint_1 = ?, hint_2 = ?, hint_3 = ?, solution = ?, explanation = ?, xp_reward = ?, payload = ?::jsonb,
                        course_id = ?, lesson_id = ?, updated_at = now()
                    where id = ?
                    """, args.toArray());
        }
        return new ExerciseSaved(exercise(id), check(id, adminId));
    }

    /** Soumet la correction de référence au correcteur, comme le ferait une apprenante. */
    public CheckResult check(long id, long adminId) {
        ExerciseDetail e = exercise(id);
        JsonNode answer;
        try {
            answer = switch (e.kind()) {
                case "COMPLETER" -> {
                    var blanks = json.createArrayNode();
                    e.payload().path("blanks").forEach(b -> blanks.add(b.path(0).asText()));
                    yield blanks;
                }
                case "REMETTRE_ORDRE" -> {
                    var order = json.createArrayNode();
                    for (int i = 0; i < e.payload().path("lines").size(); i++) {
                        order.add(i);
                    }
                    yield order;
                }
                case "MCD", "MCD_VERS_MLD" -> e.payload().path("solutionModel");
                default -> json.getNodeFactory().textNode(e.solution());
            };
            var result = exercises.preview(adminId, e.slug(), answer);
            return new CheckResult(result.success(), result.message(), result.feedback());
        } catch (BusinessRuleException ex) {
            return new CheckResult(false, ex.getMessage(), List.of());
        }
    }

    @Transactional
    public void deleteExercise(long id) {
        ExerciseDetail e = exercise(id);
        if (e.attempts() > 0) {
            throw new ConflictException("Des apprenantes ont déjà travaillé sur cet exercice : il ne peut pas être "
                    + "supprimé. Détache-le de sa leçon si besoin.");
        }
        Integer referenced = jdbc.queryForObject("""
                select count(*) from lessons l, jsonb_array_elements(l.blocks) b where b ->> 'ref' = ?
                """, Integer.class, e.slug());
        if (referenced != null && referenced > 0) {
            throw new ConflictException("Cet exercice est affiché dans une leçon : retire-le d'abord des blocs de la leçon.");
        }
        jdbc.update("delete from exercises where id = ?", id);
    }

    // ------------------------------------------------------------------ utilitaires

    private long courseOfChapter(long chapterId) {
        List<Long> ids = jdbc.queryForList("select course_id from modules where id = ?", Long.class, chapterId);
        if (ids.isEmpty()) {
            throw new NotFoundException("Chapitre introuvable.");
        }
        return ids.getFirst();
    }

    /** Échange la position avec la voisine (contrainte d'unicité différée en fin de transaction). */
    private void swap(String table, String parentColumn, long parentId, long id, int delta) {
        int position = jdbc.queryForObject("select position from " + table + " where id = ?", Integer.class, id);
        List<Map<String, Object>> neighbour = jdbc.queryForList("select id, position from " + table + " where "
                + parentColumn + " = ? and position " + (delta < 0 ? "<" : ">") + " ? order by position "
                + (delta < 0 ? "desc" : "asc") + " limit 1", parentId, position);
        if (neighbour.isEmpty()) {
            return;
        }
        long otherId = ((Number) neighbour.getFirst().get("id")).longValue();
        int otherPosition = ((Number) neighbour.getFirst().get("position")).intValue();
        jdbc.update("update " + table + " set position = ? where id = ?", otherPosition, id);
        jdbc.update("update " + table + " set position = ? where id = ?", position, otherId);
    }

    private void validateChapter(ChapterForm f) {
        requireText(f.title(), "titre", 150);
        requireText(f.summary(), "résumé", 500);
        if (f.level() == null || !LEVELS.contains(f.level())) {
            throw new BusinessRuleException("Choisis un niveau : débutant, intermédiaire, avancé ou examen.");
        }
    }

    private static int quizCount(ChapterForm f) {
        return f.quizQuestionCount() == null ? 10 : Math.clamp(f.quizQuestionCount(), 10, 20);
    }

    private static int examCount(Integer n) {
        return n == null ? 40 : Math.clamp(n, 30, 50);
    }

    private static int duration(Integer d) {
        return d == null ? 10 : Math.clamp(d, 5, 15);
    }

    private static int xp(Integer xp) {
        return xp == null ? 10 : Math.clamp(xp, 0, 200);
    }

    private static int difficulty(Integer d) {
        return d == null ? 1 : Math.clamp(d, 1, 4);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static void requireText(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("Le champ « " + label + " » est obligatoire.");
        }
        if (value.length() > max) {
            throw new BusinessRuleException("Le champ « " + label + " » dépasse " + max + " caractères.");
        }
    }

    private static String slug(String value, String label) {
        String s = value == null ? "" : value.strip();
        if (!s.matches(SLUG) || s.length() > 80) {
            throw new BusinessRuleException("Le " + label + " ne contient que des minuscules, chiffres et tirets "
                    + "(par exemple « mon-chapitre-1 »).");
        }
        return s;
    }

    private void unique(String table, String column, String value, Long exceptId) {
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ? and id <> ?",
                Integer.class, value, exceptId == null ? -1L : exceptId);
        if (n != null && n > 0) {
            throw new ConflictException("« " + value + " » est déjà utilisé : choisis un autre identifiant.");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value == null ? "null" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
