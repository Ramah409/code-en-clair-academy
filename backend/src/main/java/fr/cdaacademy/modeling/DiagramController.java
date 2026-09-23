package fr.cdaacademy.modeling;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.progress.ProgressService;
import fr.cdaacademy.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** MCD personnels de l'atelier de modélisation (enregistrés dans mcd_diagrams). */
@RestController
@RequestMapping("/api/diagrams")
@Tag(name = "Modélisation")
public class DiagramController {

    static final int MAX_CONTENT_LENGTH = 200_000;
    static final int MAX_DIAGRAMS = 50;

    public record DiagramRequest(@NotBlank(message = "Donne un titre à ton modèle.") @Size(max = 150) String title,
            @NotNull JsonNode content) {
    }

    public record DiagramSummary(long id, String title, Instant updatedAt) {
    }

    public record Diagram(long id, String title, JsonNode content, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProgressService progress;

    public DiagramController(JdbcTemplate jdbc, ObjectMapper json, ProgressService progress) {
        this.jdbc = jdbc;
        this.json = json;
        this.progress = progress;
    }

    @GetMapping
    @Operation(summary = "Mes modèles enregistrés")
    public List<DiagramSummary> list() {
        return jdbc.query("select id, title, updated_at from mcd_diagrams where user_id = ? order by updated_at desc",
                (rs, i) -> new DiagramSummary(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
                CurrentUser.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ouvrir un modèle")
    public Diagram get(@PathVariable long id) {
        List<Diagram> found = jdbc.query("""
                select id, title, content::text, updated_at from mcd_diagrams where id = ? and user_id = ?
                """, (rs, i) -> new Diagram(rs.getLong(1), rs.getString(2), read(rs.getString(3)),
                rs.getTimestamp(4).toInstant()), id, CurrentUser.id());
        if (found.isEmpty()) {
            throw new NotFoundException("Modèle introuvable.");
        }
        return found.getFirst();
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Enregistrer un nouveau modèle")
    public Diagram create(@Valid @RequestBody DiagramRequest req) {
        long userId = CurrentUser.id();
        Integer count = jdbc.queryForObject("select count(*) from mcd_diagrams where user_id = ?", Integer.class, userId);
        if (count != null && count >= MAX_DIAGRAMS) {
            throw new BusinessRuleException("Tu as atteint la limite de 50 modèles : supprime ceux dont tu n'as plus besoin.");
        }
        long id = jdbc.queryForObject("""
                insert into mcd_diagrams (user_id, title, content) values (?, ?, ?::jsonb) returning id
                """, Long.class, userId, req.title().strip(), serialize(req.content()));
        progress.award(userId, 5, 0, false);
        return get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un modèle")
    public Diagram update(@PathVariable long id, @Valid @RequestBody DiagramRequest req) {
        int updated = jdbc.update("""
                update mcd_diagrams set title = ?, content = ?::jsonb, updated_at = ? where id = ? and user_id = ?
                """, req.title().strip(), serialize(req.content()), Timestamp.from(Instant.now()), id, CurrentUser.id());
        if (updated == 0) {
            throw new NotFoundException("Modèle introuvable.");
        }
        return get(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un modèle")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        jdbc.update("delete from mcd_diagrams where id = ? and user_id = ?", id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    private String serialize(JsonNode content) {
        try {
            String s = json.writeValueAsString(content);
            if (s.length() > MAX_CONTENT_LENGTH) {
                throw new BusinessRuleException("Ce modèle est trop volumineux.");
            }
            return s;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessRuleException("Modèle illisible.");
        }
    }

    private JsonNode read(String s) {
        try {
            return json.readTree(s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
