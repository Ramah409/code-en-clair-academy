package fr.cdaacademy.project;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.security.CurrentUser;
import fr.cdaacademy.user.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projets pratiques")
public class ProjectController {

    public record CompleteStepRequest(Set<Integer> checked) {
    }

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    @Operation(summary = "Projets pratiques et avancement")
    public List<ProjectService.ProjectSummary> list() {
        return projects.list(CurrentUser.id(), isAdmin());
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Énoncé, étapes et (une fois terminé) correction d'un projet")
    public ProjectService.ProjectView view(@PathVariable String slug) {
        return projects.view(CurrentUser.id(), isAdmin(), slug);
    }

    @PostMapping("/{slug}/steps/{position}/complete")
    @Operation(summary = "Valider une étape (liste de contrôle cochée et exercice réussi)")
    public ProjectService.StepResult complete(@PathVariable String slug, @PathVariable int position,
            @RequestBody CompleteStepRequest req) {
        return projects.completeStep(CurrentUser.id(), isAdmin(), slug, position, req.checked());
    }

    private static boolean isAdmin() {
        return CurrentUser.get().role() == RoleCode.ADMIN;
    }
}
