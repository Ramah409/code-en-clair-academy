package fr.cdaacademy.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.PostgresIntegrationTest;

/** Sans Ollama, l'assistant répond quand même, à partir des cours, avec des liens vers les leçons. */
@TestPropertySource(properties = "app.ollama.base-url=http://127.0.0.1:1")
class TutorFallbackIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    static String register(MockMvc mvc, ObjectMapper json) throws Exception {
        var res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"tuteur-%s@exemple.fr","displayName":"Inès","password":"MotDePasse123","confirmPassword":"MotDePasse123","acceptTerms":true}
                """.formatted(UUID.randomUUID()))).andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void reponseDeSecoursTireeDesCours() throws Exception {
        String token = register(mvc, json);
        String status = mvc.perform(get("/api/tutor/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(status).get("aiAvailable").asBoolean()).isFalse();

        String body = mvc.perform(post("/api/tutor/ask").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"C'est quoi une clé étrangère ?\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode res = json.readTree(body);
        assertThat(res.get("aiAvailable").asBoolean()).isFalse();
        assertThat(res.at("/answer/source").asText()).isEqualTo("COURS");
        assertThat(res.at("/answer/content").asText()).contains("n'est pas disponible").containsIgnoringCase("clé étrangère");
        assertThat(res.at("/answer/links/0/url").asText()).startsWith("/lecon/");

        long id = res.get("conversationId").asLong();
        String conv = mvc.perform(get("/api/tutor/conversations/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(conv).get("messages")).hasSize(2);

        // Une autre apprenante ne peut ni lire ni supprimer cette conversation
        String other = register(mvc, json);
        mvc.perform(get("/api/tutor/conversations/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tutor/conversations/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tutor/conversations/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void questionTropLongueRefusee() throws Exception {
        String token = register(mvc, json);
        mvc.perform(post("/api/tutor/ask").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + "a".repeat(TutorService.MAX_QUESTION + 1) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
