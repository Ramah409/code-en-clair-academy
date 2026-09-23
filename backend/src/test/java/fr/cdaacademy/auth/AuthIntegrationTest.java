package fr.cdaacademy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;

class AuthIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@exemple.fr";
    }

    private MvcResult register(String email, String password) throws Exception {
        String body = """
                {"email":"%s","displayName":"Testeuse","password":"%s","confirmPassword":"%s","acceptTerms":true}
                """.formatted(email, password, password);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(cookie().httpOnly(AuthController.REFRESH_COOKIE, true))
                .andReturn();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void inscriptionPuisProfilAvecLeJeton() throws Exception {
        String email = uniqueEmail();
        JsonNode auth = body(register(email, "MotDePasse123"));

        assertThat(auth.get("user").get("role").asText()).isEqualTo("USER");
        assertThat(auth.toString()).doesNotContain("password");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.level").value(1));
    }

    @Test
    void leMotDePasseEstStockeHacheAvecBCrypt() throws Exception {
        String email = uniqueEmail();
        register(email, "MotDePasse123");
        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(hash).startsWith("$2a$12$").doesNotContain("MotDePasse123");
    }

    @Test
    void inscriptionRefuseeSiMotsDePasseDifferents() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","displayName":"Test","password":"MotDePasse123","confirmPassword":"Autre12345678","acceptTerms":true}
                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passwordConfirmed").exists());
    }

    @Test
    void connexionAvecMauvaisMotDePasseRenvoie401SansDetail() throws Exception {
        String email = uniqueEmail();
        register(email, "MotDePasse123");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"Mauvais123456\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Adresse e-mail ou mot de passe incorrect."));
    }

    @Test
    void uneApprenanteNaPasAccesALAdministration() throws Exception {
        String token = body(register(uniqueEmail(), "MotDePasse123")).get("accessToken").asText();
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void jetonFalsifieRefuse() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIn0.faux"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotationDuJetonDeRafraichissementEtDetectionDeReutilisation() throws Exception {
        MvcResult inscription = register(uniqueEmail(), "MotDePasse123");
        Cookie premier = inscription.getResponse().getCookie(AuthController.REFRESH_COOKIE);

        MvcResult renouvellement = mvc.perform(post("/api/auth/refresh").cookie(premier))
                .andExpect(status().isOk()).andReturn();
        Cookie second = renouvellement.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(second.getValue()).isNotEqualTo(premier.getValue());

        // Le premier jeton, déjà utilisé, est rejoué : toutes les sessions sont révoquées.
        mvc.perform(post("/api/auth/refresh").cookie(premier)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void suppressionDuCompteEffaceLesDonnees() throws Exception {
        String email = uniqueEmail();
        String token = body(register(email, "MotDePasse123")).get("accessToken").asText();

        mvc.perform(delete("/api/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"MotDePasse123\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isZero();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void administratriceListeEtNePeutPasSupprimerLeDernierAdmin() throws Exception {
        String token = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"formatrice@cda-academy.local\",\"password\":\"Formatrice-2026!\"}"))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();

        mvc.perform(get("/api/admin/users?search=formatrice").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"));

        Long adminId = jdbc.queryForObject("select id from users where email = 'formatrice@cda-academy.local'",
                Long.class);
        mvc.perform(delete("/api/admin/users/" + adminId).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }
}
