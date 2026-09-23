package fr.cdaacademy.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.PostgresIntegrationTest;

/** Espace Examen CDA (études de cas, jury, examens blancs ciblés) et attestations. */
class ExamAndCertificateIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private long userId;

    @BeforeEach
    void inscription() throws Exception {
        String email = "exam-" + UUID.randomUUID() + "@exemple.fr";
        var res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","displayName":"Awa Diallo","password":"MotDePasse123","confirmPassword":"MotDePasse123","acceptTerms":true}
                """.formatted(email))).andExpect(status().isCreated()).andReturn();
        token = json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
        userId = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, String body, ResultMatcher expected) throws Exception {
        builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : body);
        String content = mvc.perform(builder).andExpect(expected).andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }

    @Test
    void vueDEnsembleDeLEspaceExamen() throws Exception {
        JsonNode overview = call(get("/api/exam"), null, status().isOk());
        assertThat(overview.get("mockExams")).hasSizeGreaterThanOrEqualTo(3);
        assertThat(overview.at("/mockExams/0/finalExam").asBoolean()).isTrue();
        assertThat(overview.get("caseStudies")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(overview.at("/jury/available").asInt()).isPositive();
    }

    @Test
    void etudeDeCasRedigeePuisAutoEvaluee() throws Exception {
        String base = "/api/exam/cases/cas-reservation-salles";
        JsonNode cas = call(get(base), null, status().isOk());
        int tasks = cas.get("tasks").size();
        assertThat(cas.at("/tasks/0/model").isMissingNode()).as("corrigé caché avant la rédaction").isTrue();
        assertThat(cas.at("/tasks/0/criteria").isMissingNode()).isTrue();

        JsonNode refused = call(post(base + "/reveal"), null, status().isUnprocessableEntity());
        assertThat(refused.get("message").asText()).contains("réponse");

        StringBuilder answers = new StringBuilder("{\"answers\":{");
        for (int i = 1; i <= tasks; i++) {
            answers.append(i > 1 ? "," : "").append("\"").append(i).append("\":\"Ma réponse ").append(i).append("\"");
        }
        call(put(base + "/answers"), answers.append("}}").toString(), status().isOk());
        JsonNode revealed = call(post(base + "/reveal"), null, status().isOk());
        assertThat(revealed.at("/tasks/0/model").asText()).isNotBlank();
        assertThat(revealed.at("/tasks/0/criteria")).isNotEmpty();
        assertThat(revealed.at("/answers/1").asText()).isEqualTo("Ma réponse 1");

        JsonNode done = call(put(base + "/checks"), "{\"checked\":{\"1\":[0,1,99],\"2\":[0]}}", status().isOk());
        assertThat(done.get("completed").asBoolean()).isTrue();
        assertThat(done.get("score").asInt()).isBetween(1, 99);
        assertThat(done.at("/checked/1")).hasSize(2);
        assertThat(done.at("/reward/xpEarned").asInt()).isEqualTo(ExamService.CASE_STUDY_XP);
        JsonNode again = call(put(base + "/checks"), "{\"checked\":{\"1\":[0]}}", status().isOk());
        assertThat(again.path("reward").isNull() || again.path("reward").isMissingNode()).as("XP donné une seule fois")
                .isTrue();
    }

    @Test
    void entrainementAuxQuestionsDuJury() throws Exception {
        JsonNode session = call(get("/api/exam/jury?all=true&size=5"), null, status().isOk());
        assertThat(session).hasSize(5);
        String key = session.get(0).get("key").asText();
        assertThat(key).hasSize(40);
        call(post("/api/exam/jury/review"), "{\"key\":\"" + key + "\",\"known\":false}", status().isOk());
        JsonNode toReview = call(get("/api/exam/jury?all=true&toReview=true"), null, status().isOk());
        assertThat(toReview).hasSize(1);
        assertThat(toReview.get(0).get("known").asBoolean()).isFalse();
        call(post("/api/exam/jury/review"), "{\"key\":\"pas-une-cle\",\"known\":true}", status().isUnprocessableEntity());
    }

    @Test
    void examenBlancCibleSurUnParcours() throws Exception {
        JsonNode attempt = call(post("/api/quiz/start"), "{\"scope\":\"EXAMEN_BLANC\",\"exam\":\"examen-blanc-bases-de-donnees\"}",
                status().isOk());
        assertThat(attempt.get("items")).hasSize(25);
        Long outside = jdbc.queryForObject("""
                select count(*) from quiz_attempt_items i join questions q on q.id = i.question_id
                join courses c on c.id = q.course_id where i.attempt_id = ? and c.slug <> 'sql-postgresql'
                """, Long.class, attempt.get("id").asLong());
        assertThat(outside).isZero();
    }

    @Test
    void attestationDelivreeSeulementSurResultatsReels() throws Exception {
        JsonNode before = call(get("/api/certificates"), null, status().isOk());
        assertThat(before.get("certificates")).isEmpty();
        JsonNode global = null;
        for (JsonNode goal : before.get("goals")) {
            if (goal.get("kind").asText().equals("GLOBALE")) {
                global = goal;
            }
        }
        assertThat(global).isNotNull();
        assertThat(global.get("obtained").asBoolean()).isFalse();
        assertThat(global.get("remaining")).isNotEmpty();

        // QCM réussis pour tous les chapitres de niveau débutant du parcours SQL
        jdbc.update("""
                insert into quiz_attempts (user_id, scope, mode, course_id, module_id, question_count, submitted_at,
                                           correct_count, percent, passed)
                select ?, 'CHAPITRE', 'EXAMEN', m.course_id, m.id, 15, now(), 13, 87, true
                from modules m join courses c on c.id = m.course_id
                where c.slug = 'sql-postgresql' and m.level = 'DEBUTANT'
                """, userId);
        JsonNode after = call(get("/api/certificates"), null, status().isOk());
        assertThat(after.get("newlyIssued")).hasSize(1);
        JsonNode cert = after.at("/certificates/0");
        assertThat(cert.get("title").asText()).isEqualTo("SQL et PostgreSQL – niveau débutant");
        assertThat(cert.get("holderName").asText()).isEqualTo("Awa Diallo");
        String code = cert.get("code").asText();
        assertThat(code).matches("CEC-[A-Z2-9]{4}-[A-Z2-9]{4}");

        // Vérification publique, sans connexion, sans données personnelles autres que le nom affiché
        String pub = mvc.perform(get("/api/public/certificates/" + code)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        assertThat(pub).contains("niveau débutant").doesNotContain("@exemple.fr");
        mvc.perform(get("/api/public/certificates/CEC-0000-0000")).andExpect(status().isNotFound());

        JsonNode again = call(get("/api/certificates"), null, status().isOk());
        assertThat(again.get("newlyIssued")).isEmpty();
        assertThat(again.get("certificates")).hasSize(1);
    }
}
