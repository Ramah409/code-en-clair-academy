package fr.cdaacademy.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.PostgresIntegrationTest;

/** Parcours d'une nouvelle apprenante : leçon, questions, exercice, validation, déblocage et QCM. */
class LearningFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String token;

    @BeforeEach
    void inscription() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@exemple.fr";
        var res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","displayName":"Testeuse","password":"MotDePasse123","confirmPassword":"MotDePasse123","acceptTerms":true}
                """.formatted(email))).andExpect(status().isCreated()).andReturn();
        token = json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode call(String method, String url, String body, ResultMatcher expected) throws Exception {
        var builder = method.equals("GET") ? get(url) : post(url);
        builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : body);
        String content = mvc.perform(builder).andExpect(expected).andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }

    @Test
    void premiereLeconDeBoutEnBout() throws Exception {
        JsonNode lesson = call("GET", "/api/lessons/sql-bases-relationnelles", null, status().isOk());
        JsonNode question = null;
        for (JsonNode block : lesson.get("blocks")) {
            if (block.get("type").asText().equals("question")) {
                question = block.get("question");
                break;
            }
        }
        assertThat(question).isNotNull();
        assertThat(question.toString()).doesNotContain("\"correct\"").doesNotContain("why");

        // La leçon suivante reste verrouillée tant que celle-ci n'est pas validée
        call("GET", "/api/lessons/sql-where", null, status().isUnprocessableEntity());
        call("POST", "/api/lessons/sql-bases-relationnelles/complete", null, status().isUnprocessableEntity());

        JsonNode wrong = call("POST", "/api/lessons/sql-bases-relationnelles/questions/sql-l1-q1/answer",
                "{\"choices\":[2]}", status().isOk());
        assertThat(wrong.at("/feedback/correct").asBoolean()).isFalse();
        assertThat(wrong.at("/feedback/choices/1/why").asText()).isNotBlank();

        call("POST", "/api/lessons/sql-bases-relationnelles/questions/sql-l1-q1/answer", "{\"choices\":[1]}",
                status().isOk());
        call("POST", "/api/lessons/sql-bases-relationnelles/questions/sql-l1-q2/answer", "{\"choices\":[2]}",
                status().isOk());
        call("POST", "/api/lessons/sql-bases-relationnelles/questions/sql-l1-q3/answer", "{\"text\":\"*\"}",
                status().isOk());
        call("POST", "/api/lessons/sql-bases-relationnelles/questions/sql-l1-q4/answer", "{\"choices\":[1]}",
                status().isOk());

        JsonNode failed = call("POST", "/api/exercises/sql-ex-select-services/submit",
                "{\"answer\":\"SELECT nom FROM services\"}", status().isOk());
        assertThat(failed.get("success").asBoolean()).isFalse();
        assertThat(failed.get("feedback").get(0).asText()).contains("colonne");
        assertThat(failed.has("solution")).isFalse();

        JsonNode solved = call("POST", "/api/exercises/sql-ex-select-services/submit",
                "{\"answer\":\"select * from services;\"}", status().isOk());
        assertThat(solved.get("success").asBoolean()).isTrue();
        assertThat(solved.at("/reward/xpEarned").asInt()).isPositive();
        assertThat(solved.get("explanation").asText()).isNotBlank();

        JsonNode done = call("POST", "/api/lessons/sql-bases-relationnelles/complete", "{\"secondsSpent\":300}",
                status().isOk());
        assertThat(done.at("/next/slug").asText()).isEqualTo("sql-select-colonnes");
        assertThat(done.at("/reward/newBadges/0/code").asText()).isEqualTo("premiere-lecon");

        call("GET", "/api/lessons/sql-select-colonnes", null, status().isOk());
        JsonNode dashboard = call("GET", "/api/dashboard", null, status().isOk());
        assertThat(dashboard.at("/streak/current").asInt()).isEqualTo(1);
        assertThat(dashboard.at("/resume/lessonSlug").asText()).isEqualTo("sql-select-colonnes");
    }

    @Test
    void leQcmDeChapitreAttendLaFinDesLecons() throws Exception {
        JsonNode refused = call("POST", "/api/quiz/start",
                "{\"scope\":\"CHAPITRE\",\"course\":\"sql-postgresql\",\"chapter\":\"premiers-pas\"}",
                status().isUnprocessableEntity());
        assertThat(refused.get("message").asText()).contains("leçons");
    }

    @Test
    void entrainementAvecCorrectionImmediatePuisMesErreurs() throws Exception {
        JsonNode attempt = call("POST", "/api/quiz/start",
                "{\"scope\":\"ENTRAINEMENT\",\"mode\":\"ENTRAINEMENT\",\"course\":\"sql-postgresql\",\"count\":5}",
                status().isOk());
        assertThat(attempt.get("items")).hasSize(5);
        long id = attempt.get("id").asLong();
        for (JsonNode item : attempt.get("items")) {
            String answer = item.at("/question/kind").asText().equals("COMPLETER_CODE") ? "{\"text\":\"???\"}"
                    : "{\"choices\":[999]}";
            JsonNode r = call("POST", "/api/quiz/attempts/" + id + "/answer",
                    "{\"position\":" + item.get("position").asInt() + ",\"answer\":" + answer + "}", status().isOk());
            assertThat(r.at("/feedback/correct").asBoolean()).isFalse();
            assertThat(r.at("/feedback/explanation").asText()).isNotBlank();
        }
        JsonNode result = call("POST", "/api/quiz/attempts/" + id + "/submit", null, status().isOk());
        assertThat(result.get("percent").asInt()).isZero();
        assertThat(result.get("review")).isNotEmpty();

        JsonNode mistakes = call("POST", "/api/quiz/start", "{\"scope\":\"ERREURS\",\"count\":10}", status().isOk());
        assertThat(mistakes.get("items")).hasSize(5);
        JsonNode stats = call("GET", "/api/quiz/stats", null, status().isOk());
        assertThat(stats.get("mistakesToReview").asInt()).isEqualTo(5);
        assertThat(stats.get("recurringErrors")).hasSize(5);
    }
}
