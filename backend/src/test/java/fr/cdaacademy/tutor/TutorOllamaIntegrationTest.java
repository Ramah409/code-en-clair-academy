package fr.cdaacademy.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import fr.cdaacademy.PostgresIntegrationTest;

/** Avec un serveur Ollama (simulé), la question et le contexte de la leçon sont transmis au modèle local. */
class TutorOllamaIntegrationTest extends PostgresIntegrationTest {

    static final AtomicReference<String> LAST_REQUEST = new AtomicReference<>();
    static final HttpServer FAKE = start();

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/tags", ex -> reply(ex, "{\"models\":[{\"name\":\"modele-test:latest\"}]}"));
            server.createContext("/api/chat", ex -> {
                LAST_REQUEST.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                reply(ex, "{\"message\":{\"role\":\"assistant\",\"content\":\"Une clé étrangère, c'est comme un numéro de client sur un bon de commande.\"}}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reply(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @DynamicPropertySource
    static void ollama(DynamicPropertyRegistry registry) {
        registry.add("app.ollama.base-url", () -> "http://127.0.0.1:" + FAKE.getAddress().getPort());
        registry.add("app.ollama.model", () -> "modele-test");
    }

    @AfterAll
    static void stop() {
        FAKE.stop(0);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void reponseDuModeleLocalAvecContexte() throws Exception {
        String token = TutorFallbackIntegrationTest.register(mvc, json);
        String body = mvc.perform(post("/api/tutor/ask").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Je ne comprends pas la clé étrangère\",\"lessonSlug\":\"sor-relations\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode res = json.readTree(body);
        assertThat(res.get("aiAvailable").asBoolean()).isTrue();
        assertThat(res.at("/answer/source").asText()).isEqualTo("OLLAMA");
        assertThat(res.at("/answer/content").asText()).contains("numéro de client");

        JsonNode sent = json.readTree(LAST_REQUEST.get());
        assertThat(sent.get("model").asText()).isEqualTo("modele-test");
        assertThat(sent.get("stream").asBoolean()).isFalse();
        assertThat(sent.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(sent.at("/messages/0/content").asText()).contains("français très simple")
                .contains("Relier deux tables");
        assertThat(sent.at("/messages/1/content").asText()).isEqualTo("Je ne comprends pas la clé étrangère");
    }
}
