package fr.cdaacademy.lab;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/lab")
@Tag(name = "Laboratoire SQL")
public class LabController {

    public record RunRequest(@NotBlank(message = "Écris une requête SQL.") @Size(max = 20_000) String sql) {
    }

    private final LabService lab;

    public LabController(LabService lab) {
        this.lab = lab;
    }

    @GetMapping("/schema")
    @Operation(summary = "Tables, colonnes et clés de la base pédagogique")
    public List<LabService.Table> schema() {
        return lab.schema();
    }

    @PostMapping("/run")
    @Operation(summary = "Exécuter du SQL dans une transaction annulée (rien n'est conservé)")
    public LabService.RunResponse run(@Valid @RequestBody RunRequest req) {
        return lab.run(CurrentUser.id(), req.sql());
    }
}
