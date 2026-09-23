package fr.cdaacademy.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

/** Administration des contenus : droits, création de bout en bout, vérification de la correction, garde-fous. */
class AdminContentIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    private String account(boolean admin) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@exemple.fr";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","displayName":"Formatrice","password":"MotDePasse123","confirmPassword":"MotDePasse123","acceptTerms":true}
                """.formatted(email))).andExpect(status().isCreated());
        if (admin) {
            jdbc.update("update users set role_id = (select id from roles where code = 'ADMIN') where email = ?", email);
        }
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"MotDePasse123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    private JsonNode call(String token, MockHttpServletRequestBuilder b, String body, ResultMatcher expected)
            throws Exception {
        b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : body);
        String content = mvc.perform(b).andExpect(expected).andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }

    @Test
    void reserveAuxAdministratrices() throws Exception {
        String user = account(false);
        call(user, get("/api/admin/content/courses"), null, status().isForbidden());
        call(user, post("/api/admin/content/courses"), "{}", status().isForbidden());
    }

    @Test
    void listeDesComptesSansRecherche() throws Exception {
        String admin = account(true);
        JsonNode page = call(admin, get("/api/admin/users"), null, status().isOk());
        assertThat(page.get("totalElements").asLong()).isPositive();
        JsonNode found = call(admin, get("/api/admin/users?search=formatrice"), null, status().isOk());
        assertThat(found.get("content")).isNotEmpty();
    }

    @Test
    void creationDeBoutEnBoutEtGardeFous() throws Exception {
        String admin = account(true);
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        JsonNode course = call(admin, post("/api/admin/content/courses"), """
                {"slug":"java-test-%s","title":"Java – test","summary":"Un parcours de test.","category":"JAVA","icon":"code"}
                """.formatted(suffix), status().isCreated());
        long courseId = course.get("id").asLong();
        call(admin, post("/api/admin/content/courses"), """
                {"slug":"java-test-%s","title":"Doublon","summary":"x"}
                """.formatted(suffix), status().isConflict());

        JsonNode chapters = call(admin, post("/api/admin/content/courses/" + courseId + "/chapters"), """
                {"slug":"java-ch1-%s","title":"Variables","summary":"Les variables.","level":"DEBUTANT","quizQuestionCount":10}
                """.formatted(suffix), status().isOk());
        long chapterId = chapters.get(0).get("id").asLong();
        call(admin, post("/api/admin/content/courses/" + courseId + "/chapters"),
                "{\"slug\":\"x-" + suffix + "\",\"title\":\"T\",\"summary\":\"S\",\"level\":\"EXPERT\"}",
                status().isUnprocessableEntity());

        JsonNode lesson = call(admin, post("/api/admin/content/chapters/" + chapterId + "/lessons"), """
                {"slug":"java-l1-%s","title":"Une variable, c'est une boîte","objective":"Comprendre les variables.","duration":8}
                """.formatted(suffix), status().isCreated());
        long lessonId = lesson.get("id").asLong();
        assertThat(lesson.get("published").asBoolean()).isFalse();

        // Blocs invalides : type inconnu, puis question non rattachée
        JsonNode bad = call(admin, put("/api/admin/content/lessons/" + lessonId), """
                {"title":"T","objective":"O","blocks":[{"type":"video","md":"x"}]}
                """, status().isUnprocessableEntity());
        assertThat(bad.get("message").asText()).contains("bloc n° 1").contains("type inconnu");
        call(admin, put("/api/admin/content/lessons/" + lessonId), """
                {"title":"T","objective":"O","blocks":[{"type":"question","ref":"inexistante"}]}
                """, status().isUnprocessableEntity());

        // Question rattachée à la leçon, puis utilisée dans les blocs
        String q = """
                {"code":"java-q1-%s","kind":"CHOIX_UNIQUE","difficulty":1,"prompt":"Une variable sert à…",
                 "explanation":"Elle garde une valeur.","lessonId":%d,
                 "choices":[{"label":"garder une valeur","correct":true,"why":"Oui."},{"label":"dessiner","correct":false,"why":"Non."}]}
                """;
        JsonNode q1 = call(admin, post("/api/admin/content/questions"), q.formatted(suffix, lessonId), status().isCreated());
        call(admin, post("/api/admin/content/questions"), q.replace("java-q1", "java-q2").formatted(suffix, lessonId),
                status().isCreated());
        assertThat(q1.get("chapterId").asLong()).isEqualTo(chapterId);
        call(admin, post("/api/admin/content/questions"), """
                {"kind":"CHOIX_UNIQUE","prompt":"?","explanation":"e","choices":[{"label":"a","correct":true,"why":"w"},{"label":"b","correct":true,"why":"w"}]}
                """, status().isUnprocessableEntity());

        JsonNode saved = call(admin, put("/api/admin/content/lessons/" + lessonId), """
                {"title":"Une variable, c'est une boîte","objective":"Comprendre les variables.","duration":8,"published":true,
                 "blocks":[{"type":"definition","id":"def-var","term":"Variable","md":"Une boîte avec une étiquette."},
                           {"type":"quiz","items":[{"ref":"java-q1-%s","review":"def-var"},{"ref":"java-q2-%s"}]}]}
                """.formatted(suffix, suffix), status().isOk());
        assertThat(saved.get("blocks")).hasSize(2);
        assertThat(saved.get("published").asBoolean()).isTrue();

        // Exercice : la correction de référence est vérifiée à l'enregistrement
        String exercise = """
                {"slug":"java-ex-%s","kind":"COMPLETER","title":"Déclarer une variable","difficulty":"FACILE",
                 "statement":"Complète.","hints":["a","b","c"],"solution":"int","explanation":"Un entier.","xp":10,
                 "lessonId":%d,"payload":{"language":"java","template":"[[1]] age = 18;","blanks":[["%s"]]}}
                """;
        JsonNode ok = call(admin, post("/api/admin/content/exercises"), exercise.formatted(suffix, lessonId, "int"),
                status().isCreated());
        assertThat(ok.at("/check/success").asBoolean()).isTrue();
        assertThat(ok.at("/exercise/courseId").asLong()).isEqualTo(courseId);
        call(admin, post("/api/admin/content/exercises"), exercise.formatted(suffix + "b", lessonId, "int")
                .replace("\"hints\":[\"a\",\"b\",\"c\"]", "\"hints\":[\"a\"]"), status().isUnprocessableEntity());

        // Garde-fous de suppression
        call(admin, delete("/api/admin/content/questions/" + q1.get("id").asLong()), null, status().isConflict());
        jdbc.update("insert into progress (user_id, lesson_id, status) select id, ?, 'EN_COURS' from users limit 1",
                lessonId);
        call(admin, delete("/api/admin/content/lessons/" + lessonId), null, status().isConflict());
        call(admin, delete("/api/admin/content/courses/" + courseId), null, status().isConflict());
        jdbc.update("delete from progress where lesson_id = ?", lessonId);
        call(admin, delete("/api/admin/content/courses/" + courseId), null, status().isNoContent());
    }
}
