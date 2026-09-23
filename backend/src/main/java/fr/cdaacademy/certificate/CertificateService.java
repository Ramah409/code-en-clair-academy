package fr.cdaacademy.certificate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.course.CourseAccess;
import fr.cdaacademy.course.CourseAccess.ChapterState;
import fr.cdaacademy.course.CourseAccess.CourseState;

/**
 * Attestations délivrées uniquement sur des résultats réellement obtenus :
 * <ul>
 *   <li><b>NIVEAU</b> : tous les chapitres d'un niveau d'un parcours sont validés (QCM de chapitre réussi à 80 %,
 *       ce qui suppose toutes les leçons terminées) — par exemple « SQL et PostgreSQL – niveau débutant » ;</li>
 *   <li><b>PARCOURS</b> : tous les chapitres du parcours validés et l'examen final du parcours réussi ;</li>
 *   <li><b>GLOBALE</b> : tous les parcours obligatoires complets et l'examen blanc final réussi.</li>
 * </ul>
 * Une attestation délivrée est conservée, même si le contenu du parcours évolue ensuite.
 */
@Service
public class CertificateService {

    static final String GLOBAL_TITLE = "Code en Clair Academy – Parcours complet";
    static final List<String> LEVEL_ORDER = List.of("DEBUTANT", "INTERMEDIAIRE", "AVANCE", "EXAMEN");
    static final Map<String, String> LEVEL_TITLES = Map.of(
            "DEBUTANT", "niveau débutant",
            "INTERMEDIAIRE", "niveau intermédiaire",
            "AVANCE", "niveau avancé",
            "EXAMEN", "préparation à l'examen CDA");

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record CertificateView(String code, String kind, String title, String courseSlug, String level,
            String holderName, Instant issuedAt, JsonNode details) {
    }

    /** Objectif d'attestation et avancement de l'apprenante. */
    public record Goal(String kind, String title, String courseSlug, String level, boolean obtained, String code,
            int done, int total, List<String> remaining) {
    }

    public record Overview(List<CertificateView> certificates, List<Goal> goals, List<CertificateView> newlyIssued) {
    }

    /** Vue publique : aucune donnée personnelle hormis le nom affiché choisi par la titulaire. */
    public record PublicView(String code, String title, String holderName, Instant issuedAt, String kind) {
    }

    private final JdbcTemplate jdbc;
    private final CourseAccess access;
    private final ObjectMapper json;

    public CertificateService(JdbcTemplate jdbc, CourseAccess access, ObjectMapper json) {
        this.jdbc = jdbc;
        this.access = access;
        this.json = json;
    }

    // ------------------------------------------------------------------ calcul

    private record Candidate(String kind, String title, Long courseId, String courseSlug, String level, int done,
            int total, List<String> remaining, Map<String, Object> details) {

        boolean complete() {
            return total > 0 && done >= total;
        }
    }

    private List<Candidate> candidates(long userId) {
        List<Candidate> result = new ArrayList<>();
        List<Map<String, Object>> courses = jdbc.queryForList(
                "select slug, mandatory from courses where published order by level_number");
        List<String> missingMandatory = new ArrayList<>();
        int mandatoryDone = 0;
        int mandatoryTotal = 0;
        for (Map<String, Object> row : courses) {
            CourseState course = access.load(userId, false, (String) row.get("slug"));
            if (course.chapters().isEmpty()) {
                continue;
            }
            for (String level : LEVEL_ORDER) {
                List<ChapterState> chapters = course.chapters().stream().filter(c -> c.level().equals(level)).toList();
                if (chapters.isEmpty()) {
                    continue;
                }
                List<String> remaining = chapters.stream().filter(c -> !c.quizPassed())
                        .map(c -> "Réussir le QCM du chapitre « " + c.title() + " »").toList();
                result.add(new Candidate("NIVEAU", course.title() + " – " + LEVEL_TITLES.get(level), course.id(),
                        course.slug(), level, chapters.size() - remaining.size(), chapters.size(), remaining,
                        chapterDetails(chapters)));
            }
            List<String> remaining = new ArrayList<>(course.chapters().stream().filter(c -> !c.quizPassed())
                    .map(c -> "Réussir le QCM du chapitre « " + c.title() + " »").toList());
            if (!course.examPassed()) {
                remaining.add("Réussir l'examen final du parcours");
            }
            int total = course.chapters().size() + 1;
            Map<String, Object> details = chapterDetails(course.chapters());
            details.put("examenFinal", course.bestExamPercent());
            Candidate full = new Candidate("PARCOURS", course.title() + " – parcours complet", course.id(),
                    course.slug(), null, total - remaining.size(), total, remaining, details);
            result.add(full);
            if (Boolean.TRUE.equals(row.get("mandatory"))) {
                mandatoryTotal++;
                if (full.complete()) {
                    mandatoryDone++;
                } else {
                    missingMandatory.add("Terminer le parcours « " + course.title() + " » (chapitres et examen final)");
                }
            }
        }

        List<Map<String, Object>> finals = jdbc.queryForList("""
                select e.title, (select max(a.percent) from quiz_attempts a where a.exam_id = e.id and a.user_id = ?
                                 and a.passed) as best
                from exams e where e.final_exam and e.published and e.kind = 'EXAMEN_BLANC' order by e.position
                """, userId);
        boolean finalPassed = finals.stream().anyMatch(f -> f.get("best") != null);
        List<String> remaining = new ArrayList<>(missingMandatory);
        if (finals.isEmpty()) {
            remaining.add("L'examen blanc final n'est pas encore disponible");
        } else if (!finalPassed) {
            remaining.add("Réussir l'examen blanc final « " + finals.getFirst().get("title") + " »");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parcoursObligatoires", mandatoryTotal);
        finals.stream().filter(f -> f.get("best") != null).findFirst()
                .ifPresent(f -> details.put("examenBlancFinal", f.get("best")));
        int total = mandatoryTotal + 1;
        int done = mandatoryDone + (finalPassed ? 1 : 0);
        result.add(new Candidate("GLOBALE", GLOBAL_TITLE, null, null, null, mandatoryTotal == 0 ? 0 : done,
                mandatoryTotal == 0 ? 1 : total, remaining, details));
        return result;
    }

    private static Map<String, Object> chapterDetails(List<ChapterState> chapters) {
        Map<String, Object> details = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChapterState c : chapters) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chapitre", c.title());
            m.put("meilleurScore", c.bestQuizPercent());
            list.add(m);
        }
        details.put("chapitres", list);
        return details;
    }

    // ------------------------------------------------------------------ délivrance

    /** Délivre les attestations nouvellement méritées et les renvoie. */
    @Transactional
    public List<CertificateView> refresh(long userId) {
        String holder = jdbc.queryForObject("select display_name from users where id = ?", String.class, userId);
        List<String> issued = new ArrayList<>();
        for (Candidate c : candidates(userId)) {
            if (!c.complete()) {
                continue;
            }
            int inserted = jdbc.update("""
                    insert into certificates (code, user_id, kind, course_id, level, title, holder_name, details)
                    values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    on conflict on constraint ux_certificates_unique do nothing
                    """, newCode(), userId, c.kind(), c.courseId(), c.level(), c.title(), holder, write(c.details()));
            if (inserted > 0) {
                issued.add(c.title());
            }
        }
        if (issued.isEmpty()) {
            return List.of();
        }
        return list(userId).stream().filter(v -> issued.contains(v.title())).toList();
    }

    @Transactional
    public Overview overview(long userId) {
        List<CertificateView> newly = refresh(userId);
        List<CertificateView> owned = list(userId);
        List<Goal> goals = new ArrayList<>();
        for (Candidate c : candidates(userId)) {
            CertificateView got = owned.stream()
                    .filter(v -> v.kind().equals(c.kind()) && eq(v.courseSlug(), c.courseSlug())
                            && eq(v.level(), c.level()))
                    .findFirst().orElse(null);
            goals.add(new Goal(c.kind(), c.title(), c.courseSlug(), c.level(), got != null,
                    got == null ? null : got.code(), got != null ? c.total() : c.done(), c.total(),
                    got != null ? List.of() : c.remaining()));
        }
        return new Overview(owned, goals, newly);
    }

    public List<CertificateView> list(long userId) {
        return jdbc.query("""
                select ce.code, ce.kind, ce.title, c.slug, ce.level, ce.holder_name, ce.issued_at, ce.details::text
                from certificates ce left join courses c on c.id = ce.course_id
                where ce.user_id = ? order by ce.issued_at, ce.id
                """, (rs, i) -> new CertificateView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant(), read(rs.getString(8))), userId);
    }

    public CertificateView mine(long userId, String code) {
        return list(userId).stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new NotFoundException("Attestation introuvable."));
    }

    public PublicView verify(String code) {
        List<PublicView> found = jdbc.query("""
                select code, title, holder_name, issued_at, kind from certificates where code = ?
                """, (rs, i) -> new PublicView(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toInstant(), rs.getString(5)), code == null ? "" : code.trim().toUpperCase());
        if (found.isEmpty()) {
            throw new NotFoundException("Aucune attestation ne correspond à ce code.");
        }
        return found.getFirst();
    }

    // ------------------------------------------------------------------ utilitaires

    /** Code public lisible, sans caractères ambigus : CEC-XXXX-XXXX. */
    static String newCode() {
        StringBuilder sb = new StringBuilder("CEC-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
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
            return json.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
