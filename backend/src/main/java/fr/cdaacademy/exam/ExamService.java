package fr.cdaacademy.exam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
import fr.cdaacademy.progress.ProgressService;
import fr.cdaacademy.progress.ProgressService.Reward;

/**
 * Espace « Examen CDA » : examens blancs chronométrés (le déroulé utilise le moteur de QCM),
 * études de cas rédigées puis auto-évaluées avec une grille, et entraînement aux questions du jury.
 */
@Service
public class ExamService {

    /** XP pour une étude de cas menée jusqu'à l'auto-évaluation (une seule fois). */
    static final int CASE_STUDY_XP = 30;
    static final int MAX_ANSWER_LENGTH = 6000;

    public record AttemptSummary(long id, Integer percent, Boolean passed, Instant startedAt, Instant submittedAt) {
    }

    public record MockExam(String slug, String title, String description, String level, int durationMinutes,
            int questionCount, int passingScore, boolean finalExam, List<String> courses, Integer bestPercent,
            boolean passed, List<AttemptSummary> attempts) {
    }

    public record CaseSummary(String slug, String title, String description, String level, int durationMinutes,
            int taskCount, boolean started, boolean revealed, boolean completed, Integer score) {
    }

    public record JuryStats(int total, int known, int toReview, int available) {
    }

    public record Overview(List<MockExam> mockExams, List<CaseSummary> caseStudies, JuryStats jury) {
    }

    public record CaseStudy(String slug, String title, String description, String level, int durationMinutes,
            JsonNode context, JsonNode tasks, Map<String, String> answers, Map<String, List<Integer>> checked,
            boolean revealed, boolean completed, Integer score, Reward reward) {
    }

    public record JuryCard(String key, String question, String answer, String tip, String source, String courseSlug,
            Boolean known) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProgressService progress;

    public ExamService(JdbcTemplate jdbc, ObjectMapper json, ProgressService progress) {
        this.jdbc = jdbc;
        this.json = json;
        this.progress = progress;
    }

    // ------------------------------------------------------------------ vue d'ensemble

    @Transactional(readOnly = true)
    public Overview overview(long userId) {
        List<MockExam> mocks = jdbc.query("""
                select e.id, e.slug, e.title, e.description, e.level, e.duration_minutes, coalesce(e.question_count, 40),
                       e.passing_score, e.final_exam, e.content::text
                from exams e where e.published and e.kind = 'EXAMEN_BLANC' order by e.position, e.id
                """, (rs, i) -> {
            long id = rs.getLong(1);
            List<AttemptSummary> attempts = jdbc.query("""
                    select id, percent, passed, started_at, submitted_at from quiz_attempts
                    where user_id = ? and exam_id = ? order by started_at desc limit 10
                    """, (r, j) -> new AttemptSummary(r.getLong(1), (Integer) r.getObject(2), (Boolean) r.getObject(3),
                    r.getTimestamp(4).toInstant(), r.getTimestamp(5) == null ? null : r.getTimestamp(5).toInstant()),
                    userId, id);
            Integer best = attempts.stream().map(AttemptSummary::percent).filter(p -> p != null)
                    .max(Integer::compare).orElse(null);
            boolean passed = attempts.stream().anyMatch(a -> Boolean.TRUE.equals(a.passed()));
            List<String> courses = new ArrayList<>();
            read(rs.getString(10)).path("courses").forEach(c -> courses.add(c.asText()));
            return new MockExam(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6),
                    rs.getInt(7), rs.getInt(8), rs.getBoolean(9), courses, best, passed, attempts);
        });

        List<CaseSummary> cases = jdbc.query("""
                select e.slug, e.title, e.description, e.level, e.duration_minutes,
                       jsonb_array_length(coalesce(e.content -> 'tasks', '[]'::jsonb)),
                       a.user_id is not null, coalesce(a.revealed, false), coalesce(a.completed, false),
                       e.content::text, a.checked::text
                from exams e left join case_study_answers a on a.exam_id = e.id and a.user_id = ?
                where e.published and e.kind = 'ETUDE_DE_CAS' order by e.position, e.id
                """, (rs, i) -> new CaseSummary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getInt(5), rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8), rs.getBoolean(9),
                rs.getBoolean(9) ? score(read(rs.getString(10)).path("tasks"), checkedMap(rs.getString(11))) : null),
                userId);

        List<JuryCard> deck = juryDeck(userId, null, false);
        int known = (int) deck.stream().filter(c -> Boolean.TRUE.equals(c.known())).count();
        int toReview = (int) deck.stream().filter(c -> Boolean.FALSE.equals(c.known())).count();
        int all = juryDeck(userId, null, true).size();
        return new Overview(mocks, cases, new JuryStats(deck.size(), known, toReview, all));
    }

    // ------------------------------------------------------------------ études de cas

    @Transactional(readOnly = true)
    public CaseStudy caseStudy(long userId, String slug) {
        return caseView(userId, slug, null);
    }

    private CaseStudy caseView(long userId, String slug, Reward reward) {
        List<CaseStudy> found = jdbc.query("""
                select e.slug, e.title, e.description, e.level, e.duration_minutes, e.content::text,
                       a.answers::text, a.checked::text, coalesce(a.revealed, false), coalesce(a.completed, false)
                from exams e left join case_study_answers a on a.exam_id = e.id and a.user_id = ?
                where e.slug = ? and e.published and e.kind = 'ETUDE_DE_CAS'
                """, (rs, i) -> {
            JsonNode content = read(rs.getString(6));
            boolean revealed = rs.getBoolean(9);
            ArrayNode tasks = json.createArrayNode();
            for (JsonNode t : content.path("tasks")) {
                ObjectNode task = json.createObjectNode();
                task.put("title", t.path("title").asText());
                task.put("md", t.path("md").asText());
                if (t.hasNonNull("hint")) {
                    task.put("hint", t.path("hint").asText());
                }
                if (revealed) {
                    // Le corrigé et la grille ne sont envoyés qu'après la rédaction
                    task.put("model", t.path("model").asText());
                    task.set("criteria", t.path("criteria"));
                } else {
                    task.put("criteriaCount", t.path("criteria").size());
                }
                tasks.add(task);
            }
            ObjectNode context = json.createObjectNode();
            context.put("md", content.path("context").asText());
            context.set("documents", content.path("documents").isArray() ? content.path("documents")
                    : json.createArrayNode());
            Map<String, List<Integer>> checked = checkedMap(rs.getString(8));
            boolean completed = rs.getBoolean(10);
            return new CaseStudy(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                    context, tasks, answersMap(rs.getString(7)), checked, revealed, completed,
                    completed ? score(content.path("tasks"), checked) : null, reward);
        }, userId, slug);
        if (found.isEmpty()) {
            throw new NotFoundException("Étude de cas introuvable.");
        }
        return found.getFirst();
    }

    @Transactional
    public CaseStudy saveAnswers(long userId, String slug, Map<String, String> answers) {
        long examId = caseId(slug);
        int taskCount = taskCount(examId);
        Map<String, String> clean = new java.util.LinkedHashMap<>();
        if (answers != null) {
            answers.forEach((k, v) -> {
                int index = parseIndex(k, taskCount);
                String text = v == null ? "" : v.strip();
                if (text.length() > MAX_ANSWER_LENGTH) {
                    throw new BusinessRuleException("Une réponse dépasse " + MAX_ANSWER_LENGTH + " caractères.");
                }
                clean.put(String.valueOf(index), text);
            });
        }
        jdbc.update("""
                insert into case_study_answers (user_id, exam_id, answers) values (?, ?, ?::jsonb)
                on conflict (user_id, exam_id) do update set answers = excluded.answers, updated_at = now()
                """, userId, examId, write(clean));
        return caseView(userId, slug, null);
    }

    @Transactional
    public CaseStudy reveal(long userId, String slug) {
        long examId = caseId(slug);
        int taskCount = taskCount(examId);
        Map<String, String> answers = answersMap(jdbc.query(
                "select answers::text from case_study_answers where user_id = ? and exam_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, userId, examId));
        List<Integer> missing = new ArrayList<>();
        for (int i = 1; i <= taskCount; i++) {
            if (answers.getOrDefault(String.valueOf(i), "").isBlank()) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Écris d'abord une réponse, même courte, pour chaque tâche (il manque : "
                    + String.join(", ", missing.stream().map(String::valueOf).toList())
                    + "). C'est en essayant que l'on apprend, même si la réponse est incomplète.");
        }
        jdbc.update("update case_study_answers set revealed = true, updated_at = now() where user_id = ? and exam_id = ?",
                userId, examId);
        return caseView(userId, slug, null);
    }

    @Transactional
    public CaseStudy saveChecks(long userId, String slug, Map<String, List<Integer>> checked) {
        long examId = caseId(slug);
        JsonNode tasks = read(jdbc.queryForObject("select content::text from exams where id = ?", String.class, examId))
                .path("tasks");
        Boolean revealed = jdbc.query("select revealed from case_study_answers where user_id = ? and exam_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null, userId, examId);
        if (!Boolean.TRUE.equals(revealed)) {
            throw new BusinessRuleException("Affiche d'abord le corrigé pour t'auto-évaluer.");
        }
        Map<String, List<Integer>> clean = new java.util.LinkedHashMap<>();
        if (checked != null) {
            checked.forEach((k, v) -> {
                int index = parseIndex(k, tasks.size());
                int criteria = tasks.get(index - 1).path("criteria").size();
                clean.put(String.valueOf(index), v == null ? List.of()
                        : v.stream().filter(c -> c != null && c >= 0 && c < criteria).distinct().sorted().toList());
            });
        }
        int updated = jdbc.update("""
                update case_study_answers set checked = ?::jsonb, completed = true, updated_at = now()
                where user_id = ? and exam_id = ? and not completed
                """, write(clean), userId, examId);
        if (updated == 0) {
            jdbc.update("update case_study_answers set checked = ?::jsonb, updated_at = now() where user_id = ? and exam_id = ?",
                    write(clean), userId, examId);
        }
        Reward reward = updated > 0 ? progress.award(userId, CASE_STUDY_XP, 0, true) : null;
        return caseView(userId, slug, reward);
    }

    // ------------------------------------------------------------------ questions du jury

    /**
     * Cartes de questions du jury : blocs « jury » des leçons (déjà terminées, sauf si {@code all})
     * et questions des examens oraux. Filtre facultatif par parcours.
     */
    @Transactional(readOnly = true)
    public List<JuryCard> juryDeck(long userId, String course, boolean all) {
        List<JuryCard> cards = new ArrayList<>();
        jdbc.query("""
                select c.slug, c.title, l.title, item ->> 'q', item ->> 'a', item ->> 'tip', r.known
                from lessons l
                join modules m on m.id = l.module_id
                join courses c on c.id = m.course_id and c.published
                cross join lateral jsonb_array_elements(l.blocks) b
                cross join lateral jsonb_array_elements(b -> 'items') item
                left join jury_reviews r on r.user_id = ? and r.item_key = encode(sha256(convert_to(item ->> 'q', 'UTF8')), 'hex')::varchar(40)
                where l.published and b ->> 'type' = 'jury' and (?::text is null or c.slug = ?)
                  and (? or exists (select 1 from progress p where p.user_id = ? and p.lesson_id = l.id
                                     and p.status = 'TERMINEE'))
                order by c.level_number, m.position, l.position
                """, rs -> {
            cards.add(new JuryCard(key(rs.getString(4)), rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(2) + " — " + rs.getString(3), rs.getString(1), (Boolean) rs.getObject(7)));
        }, userId, course, course, all, userId);
        if (course == null) {
            jdbc.query("""
                    select e.title, q ->> 'q', q ->> 'a', q ->> 'tip', q ->> 'theme'
                    from exams e cross join lateral jsonb_array_elements(e.content -> 'questions') q
                    where e.published and e.kind = 'ORAL' order by e.position
                    """, rs -> {
                String k = key(rs.getString(2));
                Boolean known = jdbc.query("select known from jury_reviews where user_id = ? and item_key = ?",
                        r -> r.next() ? r.getBoolean(1) : null, userId, k);
                String theme = rs.getString(5);
                cards.add(new JuryCard(k, rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(1) + (theme == null ? "" : " — " + theme), null, known));
            });
        }
        // Une même question peut figurer dans plusieurs leçons : on la garde une fois
        java.util.LinkedHashMap<String, JuryCard> unique = new java.util.LinkedHashMap<>();
        cards.forEach(c -> unique.putIfAbsent(c.key(), c));
        return new ArrayList<>(unique.values());
    }

    /** Séance d'entraînement : questions à revoir d'abord, puis jamais vues, puis déjà sues, mélangées par groupe. */
    @Transactional(readOnly = true)
    public List<JuryCard> session(long userId, String course, boolean all, boolean onlyToReview, int size) {
        List<JuryCard> deck = juryDeck(userId, course, all);
        List<JuryCard> review = new ArrayList<>(deck.stream().filter(c -> Boolean.FALSE.equals(c.known())).toList());
        List<JuryCard> fresh = new ArrayList<>(deck.stream().filter(c -> c.known() == null).toList());
        List<JuryCard> known = new ArrayList<>(deck.stream().filter(c -> Boolean.TRUE.equals(c.known())).toList());
        Collections.shuffle(review);
        Collections.shuffle(fresh);
        Collections.shuffle(known);
        List<JuryCard> result = new ArrayList<>(review);
        if (!onlyToReview) {
            result.addAll(fresh);
            result.addAll(known);
        }
        return result.subList(0, Math.min(Math.clamp(size, 1, 50), result.size()));
    }

    @Transactional
    public void review(long userId, String key, boolean known) {
        if (key == null || !key.matches("[0-9a-f]{40}")) {
            throw new BusinessRuleException("Question inconnue.");
        }
        jdbc.update("""
                insert into jury_reviews (user_id, item_key, known) values (?, ?, ?)
                on conflict (user_id, item_key) do update set known = excluded.known, times = jury_reviews.times + 1,
                    reviewed_at = now()
                """, userId, key, known);
    }

    // ------------------------------------------------------------------ utilitaires

    /** Clé stable d'une question du jury : SHA-256 du texte, tronqué à 40 caractères hexadécimaux. */
    static String key(String question) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(question.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Integer score(JsonNode tasks, Map<String, List<Integer>> checked) {
        int total = 0;
        int ok = 0;
        for (int i = 0; i < tasks.size(); i++) {
            total += tasks.get(i).path("criteria").size();
            ok += checked.getOrDefault(String.valueOf(i + 1), List.of()).size();
        }
        return total == 0 ? 0 : Math.round(100f * ok / total);
    }

    private long caseId(String slug) {
        List<Long> ids = jdbc.queryForList("select id from exams where slug = ? and published and kind = 'ETUDE_DE_CAS'",
                Long.class, slug);
        if (ids.isEmpty()) {
            throw new NotFoundException("Étude de cas introuvable.");
        }
        return ids.getFirst();
    }

    private int taskCount(long examId) {
        return jdbc.queryForObject("select jsonb_array_length(coalesce(content -> 'tasks', '[]'::jsonb)) from exams where id = ?",
                Integer.class, examId);
    }

    private static int parseIndex(String key, int count) {
        try {
            int index = Integer.parseInt(key);
            if (index >= 1 && index <= count) {
                return index;
            }
        } catch (NumberFormatException e) {
            // traité ci-dessous
        }
        throw new BusinessRuleException("Tâche inconnue : " + key);
    }

    private Map<String, String> answersMap(String raw) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        read(raw).properties().forEach(f -> map.put(f.getKey(), f.getValue().asText()));
        return map;
    }

    private Map<String, List<Integer>> checkedMap(String raw) {
        Map<String, List<Integer>> map = new java.util.LinkedHashMap<>();
        read(raw).properties().forEach(f -> {
            List<Integer> list = new ArrayList<>();
            f.getValue().forEach(v -> list.add(v.asInt()));
            map.put(f.getKey(), list);
        });
        return map;
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
